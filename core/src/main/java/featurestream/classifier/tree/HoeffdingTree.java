/* 
 * Copyright (C) 2013 Andrew Twigg - All Rights Reserved
 * Unauthorized copying or distribution 
 * of this file, via any medium, is strictly prohibited
 * Proprietary and confidential
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package featurestream.classifier.tree;

import com.bigml.histogram.Histogram;
import com.bigml.histogram.MixedInsertException;
import com.bigml.histogram.NumericTarget;
import featurestream.classifier.Learner;
import featurestream.classifier.summary.ResultSummarizer;
import featurestream.classifier.tree.dt.*;
import featurestream.classifier.tree.split.Split;
import featurestream.classifier.tree.split.SplitEvaluator;
import featurestream.classifier.tree.split.SplitGenerator;
import featurestream.data.Instance;
import featurestream.data.schema.Schema;
import featurestream.utils.DataUtils;
import featurestream.utils.FeaturestreamException;
import featurestream.utils.Stats;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.function.Functions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * 1-pass streaming decision tree
 * using hoeffding bounds and histograms
 * 
 */
public abstract class HoeffdingTree extends Learner {
	
  private transient static final Logger log = LoggerFactory.getLogger(HoeffdingTree.class);	

  // params
  static final int HISTOGRAM_BUCKETS = 64;
  static final int NUM_SPLITS = 16;
  final double EPSILON;
  final int SPLIT_FREQ; // number of updates to a leaf l between calls to examineLeaf(l)
  public final int MAX_DEPTH;
  final double DELTA;
  final double TAU; // VFDT default
  final int FEATURE_SIZE; // size of feature vector returned from transform(.)
  final boolean INTEGRATE_MISSING;
  final boolean NORMALIZE_PREDICTION;
  final private int a;
  final private int b;
  final private int p = 263;

  Random rng;

  SplitGenerator split_generator; // used to generate candidate splits
  SplitEvaluator split_evaluator; // used to evaluate splits
  
  ResultSummarizer summarizer;

  Tree tree;

  // map from leaf ids -> attribute -> histogram
  public class State implements Serializable {
	  public Map<Integer, Map<Integer,Histogram>> histograms;
	  public State() {
		  histograms = new HashMap<Integer,Map<Integer,Histogram>>();
	  }
  }

  public State state;
  public Schema schema;
  protected boolean is_regression;
  protected String target;
  protected int mtry;// number of attributes to examine per split
  protected List<Node> internal_nodes;

  public HoeffdingTree(Schema schema, String target, Properties props, SplitGenerator generator, SplitEvaluator evaluator, boolean is_regression) {	  
	  mtry = 0;
	  this.is_regression = is_regression;
	  this.schema = schema;
	  this.target = target;
	  this.split_evaluator = evaluator;
	  this.split_generator = generator;
	  internal_nodes = new ArrayList<Node>();
	  this.summarizer = ResultSummarizer.getSummarizer(is_regression, schema);
	  
	  assert (schema!=null);
	  assert (generator!=null);
	  assert (evaluator!=null);

	  state = new State();
	  tree = new Tree();

      // HISTOGRAM_BUCKETS = Integer.parseInt(props.getProperty("HISTOGRAM_BUCKETS", "64"));
      // NUM_SPLITS = Integer.parseInt(props.getProperty("NUM_SPLITS", "16"));
      EPSILON = Double.parseDouble(props.getProperty("EPSILON", "1e-6"));
      SPLIT_FREQ = Integer.parseInt(props.getProperty("SPLIT_FREQ", "100")); // number of updates to a leaf l between calls to examineLeaf(l)
      MAX_DEPTH = Integer.parseInt(props.getProperty("MAX_DEPTH", "10"));
      DELTA = Double.parseDouble(props.getProperty("DELTA", "1e-5"));
      TAU = Double.parseDouble(props.getProperty("TAU", "0.05")); // VFDT default
      FEATURE_SIZE = Integer.parseInt(props.getProperty("FEATURE_SIZE", "32")); // size of feature vector returned from transform(.)
      INTEGRATE_MISSING = Boolean.parseBoolean(props.getProperty("INTEGRATE_MISSING", "true"));
      NORMALIZE_PREDICTION = Boolean.parseBoolean(props.getProperty("INTEGRATE_MISSING", "false"));

      rng = new Random();
      // constants for feature hashing
      this.a = rng.nextInt(FEATURE_SIZE);
      this.b = rng.nextInt(FEATURE_SIZE);

	  log.info("created HoeffdingTree: target={}, split_generator={}, split_evaluator={}, is_regression={}", target, generator, evaluator, is_regression);
  }

  @Override
  public void update(Instance instance) {
	  if (instance==null)
		  return;

	  double label = instance.getLabel();

	  log.debug("train, instance={}, label={}, target={}", instance, label, target);

	  // update summary	  
	  double predicted = predict(instance);
	  summarizer.add(predicted, label);

	  Node n = tree.root.walkTree(instance, true, INTEGRATE_MISSING);
	  // walk back up the tree, updating labels
	  Node n1 = n;
	  while (n1!=null) {
		  updateLabel(n1,instance,label);
		  n1=n1.getParent();
	  }
	  
	  if (n instanceof Leaf) {
		  // update histograms - only need them at leaves
		  // FIXME use histograms at every node for distributional info
		  updateHistograms(tree, schema, state.histograms, (Leaf)n, instance, label, is_regression, MAX_DEPTH);
		  // periodically check for new splits
		  if (n.n_samples % SPLIT_FREQ == 0)
			  updateLeaf(state.histograms, (Leaf)n);
	  }
	}

// examine the leaf, and update the tree if necessary
  // return true if the tree changed
  public boolean updateLeaf(Map<Integer,Map<Integer,Histogram>> histograms, Leaf l) {

	  // update node label using histogram
      updateLabel(l, histograms.get(l.id).get(0));

	  if (l.getDepth() >= MAX_DEPTH)
		  // cannot be expanded
		  return false;

	  // check if leaf needs expanding
	  Node n = examineLeaf(l,histograms.get(l.id));
	  if (n!=l) {
		  // attach n into the tree; fix up the parent pointer of n
		  Node parent = l.getParent();
		  // handle root case
		  if (parent == null)
			  tree.root=n;
		  else
			  parent.replaceChild(l, n);
		  
		  // n is now an internal node
		  internal_nodes.add(n);

		  // remove l from histogram map
		  histograms.remove(l.id);

		  return true;
	  } else 
		  return false;
	  
  }

  public Tree getTree() {
	  return tree;
  }
  
  public boolean isRegression() {
	  return is_regression;
  }
  
  /** 
   * examine leaf - it may be expanded
   * 
   * @param l the unlabeled leaf to expand
   * @param histograms the histograms for the leaf l
   * @return the node in the position occupied by l - either a labeled leaf or a new node
   * @throws MixedInsertException 
   */
  protected Node examineLeaf(Leaf l, Map<Integer,Histogram> histograms) {

      if (DataUtils.nnz(l.label_counts) == 1) {
          log.debug("leaf={} has a single label count; not examining further", l);
          return l;
      }

      // check if target histogram has only one bin
//      if (histograms.get(0).getBins().size() <= 1) {
//          log.debug("leaf={} target histogram={} has a single bucket; not examining further", l, histograms.get(0));
//          return l;
//      }

      int nAttrs = schema.numAttrs();
//	  log.info("examine leaf={}, histograms={}, n_attrs={}", l, histograms,n_attrs);
	  // update mtry (as # attributes changes)
	  mtry = (int)((is_regression) ? Math.ceil(nAttrs / 3.0) : Math.ceil(Math.sqrt(nAttrs)));
	  
	  if (mtry == 0)
		  // nothing to do; return the current leaf
		  return l;
	  
	  // consider only a random subset of mtry attributes per node
	  // only consider those with nonzero count
	  int[] leaf_attr_counts = new int[nAttrs];
	  for (Map.Entry<Integer,Histogram> label_histograms : histograms.entrySet()) {
		  int attr=label_histograms.getKey();
		  Histogram h = label_histograms.getValue();
		  if (h==null) // discarded attribute
			  continue;
		  assert (attr < nAttrs);
		  leaf_attr_counts[attr] += h.getTotalCount();
	  }
	  
	  Set<Integer> attrs = randomAttributes(mtry, leaf_attr_counts);
	  assert (attrs.size() > 0);
	  
	  // improve this from O(n log n) -> O(n)
	  // want the split with largest G
	  PriorityQueue<Split> splits = new PriorityQueue<Split>(nAttrs, Collections.reverseOrder());
	  double max_G=0;
	  for (int attr : attrs) { //
		  Histogram h = histograms.get(attr);
		  // precompute max_G for hoeffding bound if regressing
		  if (is_regression) {
			  NumericTarget t_inf = (NumericTarget) h.getTotalTargetSum();
			  // an upper bound on G for regression, here taken over all attributes
			  max_G = Math.max(max_G, t_inf.getSumSquares()); 
		  }
		  
		  // now generate and evaluate splits for attr
		  List<Split> candidates = split_generator.candidateSplits(schema.getAttribute(attr),attr,h);
		  assert candidates.size() > 0;
		  // only add the best split per attribute
		  Split best = candidates.get(0);
		  for (Split split: candidates) {
 			  split = split_evaluator.evaluateSplit(split,h);
			  if (split.G > best.G)
				  best = split;
		  }
		  splits.add(best);
	  }
	  
	  // filter out non-best splits for each attribute
	  if (log.isDebugEnabled()) {
		  log.debug("--- splits ---");
		  for (Split split:splits)
			  log.debug("split={}",split);
	  }

	  assert (splits.size() > 0);
	  Split split1 = splits.remove(); // best split
	  assert (split1!=null);
	  assert (!Double.isNaN(split1.G));
	  // 2nd best split - on different attribute
	  // might be null if have very few attributes
	  Split split2 = splits.poll();
	  
	  // Hoeffding bound
	  double R; // range of G
	  if (!is_regression) 
		  // theory says lg(n_values) but histograms are approx so add bit of flexibility
		  R = FastMath.log(2.0, 1+schema.getNumLabels());
	  else
		  R = max_G;
	  
	  double log_inv_delta = FastMath.log(1.0/ DELTA); // ~7 when DELTA = 1e-3
	  double n_l = l.count;//leaf_counts.get(l.id);
	  double eps = (DELTA ==1.0)? 0.0 : R*FastMath.sqrt(log_inv_delta/(2*n_l));
	  log.debug("hoeffding bounds: R={}, n_l={}, eps={}, TAU={}, is_regression={}",R,n_l,eps, TAU,is_regression);

	  if (split1.G <= eps) {
		  // splits are all too bunched up to determine anything useful
		  // or the leaf cannot be split further
		  return l;
	  }

	  // sanity checks
	  if (log.isDebugEnabled()) {
		  log.debug("splits:\nsplit1={}\nsplit2={}",split1,split2);
		  log.debug("split1={}, R={}, target={} ({})",split1.G,R,target,schema.getNumLabels());
	  }
	  if (split2 != null) {
		  assert (split1.attr != split2.attr);
		  assert (split1.G >= split2.G);
		  assert (!Double.isNaN(split2.G));
	  }
	  assert (split1.G <= R+EPSILON);
	  assert (!Double.isNaN(n_l));

	  if ((split2==null) || (split1.G - split2.G > eps) || (eps < TAU)) {
		  // split l on split1
		  Split split = split1;
		  log.debug("splitting: split={}",split);
		  
		  Node n;
		  if (split.is_numeric) {
			  assert (!Double.isNaN(split.tau));
			  assert (split.tau < 1.0); // not all go to the left
			  assert (split.tau> 0.0); // not all go to the right
			  // split leaf l on (best_attr,best_split)
			  // initially new leaves are unlabeled
			  Leaf left = new Leaf(null, tree); // alternatively, reuse id of parent for one of the leaves
			  Leaf right = new Leaf(null, tree);
			  n = new NumericalNode(null,split.attr,split.split,left,right);
			  left.setParent(n);
			  right.setParent(n);		  
		  }
		  else {
			  Leaf l_split = new Leaf(null, tree);
			  Leaf l_rest = new Leaf(null, tree);
			  n = new CategoricalNode(null,split.attr, split.split, l_rest, l_split);
//			  log.info("expanding: split={} n={},l_split={},l_rest={}",split,n,l_split,l_rest);
			  l_split.setParent(n);
			  l_rest.setParent(n);
		  }
		  
		  // update split gain for importances
		  n.G = split1.G;

          // update counts for internal node
          n.label_counts = l.label_counts;
          n.sum = l.sum;
          n.count = l.count;
		  
		  return n;
	  }
	  else {
  		  // no split yet
		  // early attribute discarding
		  // discard any attributes with best split.G < split1.G-eps
/*		  double t = split1.G - eps;
		  assert (t>=0.0);
		  for (Split s : splits)
			  if (s.G < t) { // split1.G - s.G > eps
				  histograms.put(s.attr, null); // mark attr as discarded
			  }
*/
		  return l;
	  }
  }
  
	// compute unit vector of feature importances; returns zero vector if there are no internal nodes
	public Vector featureImportances() {
		// normalize(sum_n n.G*n.n_samples) for internal nodes n
		RandomAccessSparseVector v = new RandomAccessSparseVector(schema.numAttrs());
		double sum=0;
		for (Node n: internal_nodes) {
			sum += n.G*n.n_samples;
			v.set(n.attr, v.get(n.attr) + n.G*n.n_samples); // > 1 internal node might split at attr
		}
		return (sum > 0.0) ? v.assign(Functions.div(sum)) : v;
	}
	
	// compute a vector of feature importances for a single instance
	public Vector featureImportances(Instance instance) {
		RandomAccessSparseVector v = new RandomAccessSparseVector(schema.numAttrs());
		if (instance == null)
			return v;
		
		Node n = tree.root.walkTree(instance, false, this.INTEGRATE_MISSING);
		// walk back up the tree
		double sum=0;
		while (n!=null) {
			if (!(n instanceof Leaf)) {
				sum += n.G*n.n_samples;
				v.set(n.attr, v.get(n.attr)+n.G*n.n_samples);
			}
			n=n.getParent();
		}
		return (sum>0.0) ? v.assign(Functions.div(sum)) : v;
	}

	// returns a vector with a single bit set corresponding to the leaf representing the instance
	public Vector transform(Instance instance) {
		RandomAccessSparseVector v = new RandomAccessSparseVector(FEATURE_SIZE);
		if (instance == null)
			return v;

		Node n = tree.root.walkTree(instance, false, false); // TODO should last arg=INTEGRATE_MISSING ?
		if (n instanceof Leaf) {
			Leaf l = (Leaf) n;
			int ix = ((this.a*l.id + this.b) % this.p) % FEATURE_SIZE;
			v.set(ix, 1.0);
		}
		//		  else
			//			  log.info("unpredictable instance; cannot transform");
		return v;
	}

  
  // static methods -------------------------------
  
  public static void updateHistograms(Tree tree, Schema schema, Map<Integer, Map<Integer,Histogram>> histograms, Leaf l, Instance instance, double label, boolean is_regression, int max_depth) {

      assert schema.isTarget(schema.getAttributeName(0));

	  // maximum depth leaves don't have histograms
      // since they will never be expanded
//	  if (l.getDepth() >= max_depth)
//		  return;
	  try {
		  // add value to histogram(l,attr,label)
		  for (int attr=0; attr<instance.getAttrs().size(); attr++) {

              // we're using histograms to summarize the label distribution too
			  double v = (attr==0) ? label : instance.get(attr);

			  // skip attributes not present
			  if (attr > 0 && Double.isNaN(v))
				  continue;

			  Histogram h = ensureHistogram(histograms, l.id, attr);
			  // if the attr has been marked to ignore at l then h == null
			  if (h != null)
				  if (is_regression)
					  h.insertNumeric(v, label);
				  else
					  h.insertCategorical(v, label);
		  }
	  } catch (MixedInsertException e) { throw new FeaturestreamException(e); }
  }

  static Histogram ensureHistogram(Map<Integer,Map<Integer,Histogram>> histograms, Integer i, int attr) {
	  if (!histograms.containsKey(i))
		  histograms.put(i, new HashMap<Integer,Histogram>());
	  Map<Integer,Histogram> m = histograms.get(i);
	  if (!m.containsKey(attr))
		  m.put(attr, new Histogram(HISTOGRAM_BUCKETS, false));
	  return m.get(attr);
  }

  
  // abstract methods -------------------------------

  // update the counts of the node given a histogram
  protected abstract void updateLabel(Node n, Histogram h);

  // update the label of the node given an instance
  protected abstract void updateLabel(Node l, Instance instance, double label);

  // select a random subset of indices i of size mtry where count[i] > 0
  private Set<Integer> randomAttributes(int m, int[] counts) {
	  // check # nonzero counts
	  int nnz=0;
	  for (int c:counts)
		  if (c>0)
			  nnz++;

	  Set<Integer> s = new HashSet<Integer>();
	  while (s.size() < m && s.size() < nnz) {
		  int i = rng.nextInt(counts.length);
		  if (counts[i] > 0)
			  s.add(i);
	  }
	  return s;
  }

  public Schema getSchema() {
	  return schema;
  }

  public String getTarget() {
	  return target;
  }
  
  public Stats getStats() {
	  return summarizer.getSummary();
  }

  @Override
  public void clearStats() {
	  summarizer.clear();
  }

  public Stats getInfo() {
	  Stats stats = tree.getStats();
	  stats.add("summary", getStats());
	  stats.add("type", this.getClass().getName());
	  stats.add("m", mtry);
	  stats.add("SPLIT_FREQ", SPLIT_FREQ);
	  stats.add("hist_buckets",HISTOGRAM_BUCKETS);
	  stats.add("num_splits",NUM_SPLITS);
	  stats.add("DELTA", DELTA);
	  stats.add("MAX_DEPTH", MAX_DEPTH);
	  stats.add("target", target);
	  return stats;
  }
  
}

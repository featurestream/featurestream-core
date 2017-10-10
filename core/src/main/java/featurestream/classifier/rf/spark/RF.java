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
package featurestream.classifier.rf.spark;

import com.bigml.histogram.Histogram;
import com.bigml.histogram.MixedInsertException;
import featurestream.classifier.summary.ResultSummarizer;
import featurestream.classifier.tree.HoeffdingTree;
import featurestream.classifier.tree.HoeffdingTree.State;
import featurestream.classifier.tree.dt.Leaf;
import featurestream.classifier.tree.dt.Node;
import featurestream.classifier.tree.dt.Tree;
import featurestream.data.Instance;
import featurestream.data.schema.Schema;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.broadcast.Broadcast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class RF {// extends SparkLearner {
	
	private transient static final Logger log = LoggerFactory.getLogger(RF.class);

	/*
	 * build an RFModel from an RDD
	 */
	public static void update(RFModel model, JavaRDD<Instance> data) {

		final Schema schema = model.getSchema();
		JavaSparkContext sc = new JavaSparkContext(data.context());
		final boolean is_regression = model.isRegression();

		int n_trees = model.forest.size();
		log.info("n_trees={}, n_instances={}",n_trees,data.count());
		for (int i=0;i<n_trees;i++) {
		
			log.info("tree {} / {}",i,n_trees);
			JavaRDD<Instance> bagged_instances = data.sample(true,1.0,42+i).cache();
			int iteration=0;
			
			// grow the ith tree
			HoeffdingTree ht = model.forest.get(i);
			boolean growing = true;	
			while (growing) {
				Tree tree = ht.getTree();
				
				int md = tree.root.maxDepth();
				log.info("iteration={}, size={}, depth={}",iteration,tree.root.nbNodes(),md);
				assert (iteration <= md+1);
				final int max_depth = ht.MAX_DEPTH;
				
				// broadcast the tree
				final Broadcast<Tree> tree_bc = sc.broadcast(tree);

				// update the histograms at each node
				Function2<State,Instance,State> updateState = new Function2<State,Instance,State>() {
					public State call(State state, Instance instance) throws Exception {
						Tree tree = tree_bc.value();
						Node n = tree.root.walkTree(instance, true, true);
						double label = instance.getLabel();
						if (n instanceof Leaf)
							HoeffdingTree.updateHistograms(tree, schema, state.histograms, (Leaf)n, instance, label, is_regression, max_depth);
						return state;
					}			
				};
				   
				// merge the leaf histograms
				Function2<State,State,State> mergeState = new Function2<State,State,State>() {
					public State call(State s1, State s2) {
						for (Integer l2 : s2.histograms.keySet()) {
							for (Map.Entry<Integer,Histogram> e : s2.histograms.get(l2).entrySet()) {
								Map<Integer,Histogram> m1 = s1.histograms.get(l2);
								if (m1 == null) {
									m1 = new HashMap<Integer,Histogram>();
									s1.histograms.put(l2,m1);
								}
								Histogram h = m1.get(e.getKey());
								if (h == null)
									m1.put(e.getKey(), e.getValue());
								else
									try { m1.put(e.getKey(), h.merge(e.getValue())); } catch (MixedInsertException e1) { e1.printStackTrace(); }
							}
						}
						return s1;
					}
				};

				log.debug("aggregating over {} instances",bagged_instances.count());

				State s = bagged_instances.aggregate(ht.state, updateState, mergeState);
				ht.state = s;
				
				log.debug("s_leaves={}",s.histograms.keySet());

				// update all leaves
				growing = false;
				Iterable<Integer> leaves = new ArrayList<Integer>(s.histograms.keySet());
				for (Integer l:leaves) {
					Leaf leaf = tree.getLeaf(l);
                    log.debug("updating leaf={} depth={} md={}",leaf,leaf.getDepth(),md);

					growing = ht.updateLeaf(s.histograms,leaf) || growing;
                    log.debug("growing={}, now leaf={}",growing,leaf);

				}
	
				log.info("post: size={} depth={}",tree.root.nbNodes(),tree.root.maxDepth());
				log.debug("tree=\n{}",tree.root);

				iteration++;
			} // next iteration
			log.info("final tree=\n{}",ht.getTree().root);

		} // next tree
		
	}

/*	public void update(JavaDStream<Instance> data) {	

		data.foreach(new Function<JavaRDD<Instance>,Void>() {
			public Void call(JavaRDD<Instance> rdd) {
				updateParallel(rdd);
				try { Model.writeModel(checkpointPath, model); }
				catch(IOException ioe) { log.error("error checkpointing model", ioe); }
				return null;
			}
		});
	}
	
*/
	public static ResultSummarizer updateSummarizer(final RFModel model, JavaRDD<Instance> data) {
		final ResultSummarizer summarizer = model.summarizer;
		if (data.count() == 0)
			return summarizer;
		final boolean regression = model.regression;
		
		final Schema schema = model.getSchema();

		ResultSummarizer s = data.mapPartitions(new FlatMapFunction<Iterator<Instance>,ResultSummarizer>() {
			
			public Iterable<ResultSummarizer> call(Iterator<Instance> arg0) throws Exception {
				ResultSummarizer summarizer = ResultSummarizer.getSummarizer(regression, schema);
				while (arg0.hasNext()) {
					Instance instance = arg0.next();
					double predicted = model.predict(instance);
					double label = instance.getLabel();
					summarizer.add(predicted, label);
				}
				log.debug("locally computed summary:{}",summarizer.getSummary());
				return Arrays.asList(summarizer);
			}			
		}).reduce(new Function2<ResultSummarizer, ResultSummarizer, ResultSummarizer>() {
			// merge schemas
			public ResultSummarizer call(ResultSummarizer s1, ResultSummarizer s2) throws Exception {
				ResultSummarizer s = s1.merge(s2);
				log.debug("merged summary:{}",s.getSummary());
				return s;
			}
		});
		return summarizer.merge(s); // do this final merge so that the arg passed is modified
	}

/*	public Stats getInfo() {
		Stats stats = new Stats();
		stats.add("type", this.getClass().getName());
		stats.add("n_learners", model.ensemble.size());
		Stats ensemble_stats = new Stats();
		int i=0;
		for (HoeffdingTree l : model.ensemble)
			ensemble_stats.add(Integer.toString(i++), l.getInfo());

		stats.add("ensemble", ensemble_stats);
		stats.add("feature_importances", schema.unmapVectorIndexes(model.featureImportances(),true));
		stats.add("feature_importances_full", schema.unmapVectorIndexes(model.featureImportances(),false));
		return stats;
	}
*/

}

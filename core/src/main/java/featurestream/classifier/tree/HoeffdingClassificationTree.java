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

import com.bigml.histogram.CategoricalTarget;
import com.bigml.histogram.Histogram;
import featurestream.classifier.tree.dt.Leaf;
import featurestream.classifier.tree.dt.Node;
import featurestream.classifier.tree.split.IgSplitEvaluator;
import featurestream.classifier.tree.split.SplitGenerator;
import featurestream.classifier.tree.split.UniformSplitGenerator;
import featurestream.data.Instance;
import featurestream.data.schema.Schema;
import featurestream.utils.DataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class HoeffdingClassificationTree extends HoeffdingTree {

	private static transient final Logger log = LoggerFactory.getLogger(HoeffdingClassificationTree.class);	

	public HoeffdingClassificationTree(Schema schema, String target, Properties props) {
		this(schema, target, props, new UniformSplitGenerator(NUM_SPLITS));
	}
	
	public HoeffdingClassificationTree(Schema schema, String target, Properties props, SplitGenerator generator) {
		super(schema,target,props,generator,new IgSplitEvaluator(),false);
	}

	@Override
	// update the label from a histogram
	protected void updateLabel(Node n, Histogram h) {
		assert (n instanceof Leaf);//only have histograms at leaves for now		
		n.count += h.getTotalCount();
		CategoricalTarget target = (CategoricalTarget) h.getTotalTargetSum();
		if (target==null)
			return;
		Map<Object,Double> counts = target.getCounts();
		for (Entry<Object,Double> e : counts.entrySet()) {
			int ix = ((Double)e.getKey()).intValue();
			if (ix >= n.label_counts.length) // resize
				n.label_counts = Arrays.copyOf(n.label_counts, 2*ix);
			n.label_counts[ix]+= e.getValue();
		}
	}
	
	// update the leaf labels from an instance
	protected void updateLabel(Node n, Instance instance, double label) {
		n.count+=1;
		assert label>=0;
		int ix = (int) label;
		if (ix >= n.label_counts.length)
			n.label_counts = Arrays.copyOf(n.label_counts, 2*ix);
		n.label_counts[ix]+=1;	
	}

	public double predict(Instance instance) {
		if (instance == null)
			return Double.NaN;
		Node n = tree.root.walkTree(instance,false,INTEGRATE_MISSING);
		return DataUtils.maxindex(n.label_counts);
		//			return n.label_counts.maxValueIndex();
	}

	public double[] predictFull(Instance instance) {
		if (instance == null)
			return null;

		Node n = tree.root.walkTree(instance,false,INTEGRATE_MISSING);
		// label p(i) = counts(i)/sum(counts)
		double[] prediction = new double[n.label_counts.length];
		if (n.count == 0)
			return prediction;
		for (int i=0;i<n.label_counts.length;i++)
			if (NORMALIZE_PREDICTION)
				prediction[i] = n.label_counts[i] / (double)n.count;
			else
				prediction[i] = n.label_counts[i];

		// don't normalize counts, so trees with more observations are weighted more
		//				prediction[i] = l.label_counts[i]/(double)l.count;
		//			return l.label_counts.divide(l.count);
		return prediction;
	}

}
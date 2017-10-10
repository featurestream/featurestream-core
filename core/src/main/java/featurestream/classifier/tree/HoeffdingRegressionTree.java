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
import com.bigml.histogram.NumericTarget;
import featurestream.classifier.tree.dt.Leaf;
import featurestream.classifier.tree.dt.Node;
import featurestream.classifier.tree.split.RegressionSplitEvaluator;
import featurestream.classifier.tree.split.SplitGenerator;
import featurestream.classifier.tree.split.UniformSplitGenerator;
import featurestream.data.Instance;
import featurestream.data.schema.Schema;
import featurestream.utils.FeaturestreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class HoeffdingRegressionTree extends HoeffdingTree {

	private static final Logger log = LoggerFactory.getLogger(HoeffdingRegressionTree.class);	

	public HoeffdingRegressionTree(Schema schema, String target, Properties props) {
		this(schema, target, props, new UniformSplitGenerator(NUM_SPLITS));
	}

	public HoeffdingRegressionTree(Schema schema, String target, Properties props, SplitGenerator generator) {
		super(schema, target, props, generator, new RegressionSplitEvaluator(),true);
	}

	// update the node label from a histogram
	@Override
	protected void updateLabel(Node n, Histogram h) {
		assert (n instanceof Leaf);//only have histograms at leaves for now
		NumericTarget target = (NumericTarget) h.getTotalTargetSum();
		if (target==null)
			return;
		n.sum += target.getSum();
		n.count += h.getTotalCount();
	}

	// update the node labels from an instance
	@Override
	protected void updateLabel(Node n, Instance instance, double label) {
		n.count+=1;
		n.sum+=label;
	}

	public double predict(Instance instance) {
		if (instance == null)
			return Double.NaN;		
		Node n = tree.root.walkTree(instance,false,INTEGRATE_MISSING);
		return n.sum/n.count;
	}

	public double[] predictFull(Instance instance) {
		throw new FeaturestreamException("predictFull not supported by RegressionTreeModel");
		// TODO: maintain sumsquares and return a distribution ?
	}
	
}

	  

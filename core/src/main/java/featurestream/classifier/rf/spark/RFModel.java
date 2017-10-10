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

import featurestream.classifier.Learner;
import featurestream.classifier.LearnerFactory;
import featurestream.classifier.LearnerFactory.LearnerType;
import featurestream.classifier.summary.ResultSummarizer;
import featurestream.classifier.tree.HoeffdingTree;
import featurestream.data.Instance;
import featurestream.data.schema.Schema;
import featurestream.utils.DataUtils;
import featurestream.utils.FeaturestreamException;
import featurestream.utils.Stats;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.function.Functions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Properties;

public class RFModel implements Serializable {

	ArrayList<HoeffdingTree> forest;
	public ResultSummarizer summarizer;
	final boolean regression;
	final int n_learners;
	Schema schema;
	String target;

	public RFModel(Schema schema, String target, LearnerType baseLearner, Properties props) {
		this.target=target;
		this.schema=schema;
		this.n_learners = Integer.parseInt(props.getProperty("N_LEARNERS","30"));
		forest = new ArrayList<HoeffdingTree>(n_learners);
		for (int i=0;i<n_learners;i++) {
			HoeffdingTree ht = (HoeffdingTree) LearnerFactory.getLearner(schema, target, baseLearner, props);
			forest.add(ht);
		}
		this.regression = forest.get(0).isRegression();
		this.summarizer = ResultSummarizer.getSummarizer(regression, schema);
	}
	
	public double predict(Instance instance) {
		if (instance == null)
			return Double.NaN;
		if (regression) {
			double sum = 0;
			int cnt = 0;
			for (Learner l: forest) {
				double prediction = l.predict(instance);
				if (!Double.isNaN(prediction)) {
					sum += prediction;
					cnt++;
				}
			}
			return (cnt>0) ? (sum/cnt) : Double.NaN;
		}
		else
			return DataUtils.maxindex(predictFull(instance));
	}
	 
	public double[] predictFull(Instance instance) {
		if (regression)
			throw new FeaturestreamException("predictFull not supported on regression learner");
		
		// get predictions
		double[][] predictions = new double[forest.size()][];
		int ix=0,s=0;
		for (Learner l: forest) {
			predictions[ix]=l.predictFull(instance);
			s=Math.max(s,predictions[ix].length);
			ix++;
		}
		// sum
		double[] prediction = new double[s];
		for (int i=0;i<predictions.length;i++) {
			for (int j=0;j<predictions[i].length;j++)
				prediction[j]+=predictions[i][j];
		}
		// normalize
		double sum=0;
		for (int i=0;i<prediction.length;i++)
			sum+=prediction[i];		
		if (sum > 0)
			for (int i=0;i<prediction.length;i++)
				prediction[i]/=sum;
		return prediction;
	}
	
	public Vector featureImportances() {
		// average the feature importances over the ensemble
		Vector importances = forest.get(0).featureImportances().like();
		for (Learner l : forest)
			importances.assign(l.featureImportances(), Functions.PLUS);
		if (importances.size() == 0)
			return importances;
		double sum = importances.aggregate(Functions.PLUS, Functions.IDENTITY);
		return (sum > 0.0) ? importances.assign(Functions.div(sum)) : importances;
	}
	
	public Stats getStats() {
		Stats stats = summarizer.getSummary();
		stats.add("n_models",forest.size());
		return stats;
	}
	
	public void clearStats() {
		summarizer.clear();
	}

	public boolean isRegression() {
		return regression;
	}

	public Stats getInfo() {
		Stats stats = new Stats();
		stats.add("type", this.getClass().getName());
		stats.add("n_learners", n_learners);
		Stats ensemble_stats = new Stats();
		int i=0;
		for (Learner l : forest)
			ensemble_stats.add(Integer.toString(i++), l.getInfo());

		stats.add("ensemble", ensemble_stats);
		stats.add("summary", getStats());
		stats.add("feature_importances", schema.unmapVectorIndexes(featureImportances(),true));
		return stats;
	}

	public Schema getSchema() {
		return schema;
	}
}
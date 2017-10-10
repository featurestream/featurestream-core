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

package featurestream.classifier.rf;

import featurestream.classifier.Learner;
import featurestream.classifier.LearnerFactory;
import featurestream.classifier.LearnerFactory.LearnerType;
import featurestream.classifier.summary.ResultSummarizer;
import featurestream.data.Instance;
import featurestream.data.schema.Schema;
import featurestream.utils.DataUtils;
import featurestream.utils.FeaturestreamException;
import featurestream.utils.Stats;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.Vector.Element;
import org.apache.mahout.math.function.Functions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class RF extends Learner {
	private static final Logger log = LoggerFactory.getLogger(RF.class);

	ArrayList<Learner> ensemble;
	// each learner receives a poisson bag sample
	List<PoissonDistribution> poisson;
	public ResultSummarizer summarizer;

	final boolean regression;
	final int n_learners;
	Schema schema;
	String target;

	public RF(Schema schema, String target, LearnerType learnerType, Properties props) {
		this.target=target;
		this.schema=schema;
		this.n_learners = Integer.parseInt(props.getProperty("n_learners","100"));
		ensemble = new ArrayList<Learner>(n_learners);
		poisson = new ArrayList<PoissonDistribution>();
		for (int i=0; i<n_learners; i++) {
			Learner l = (Learner) LearnerFactory.getLearner(schema, target, learnerType, props);
			ensemble.add(l);
			poisson.add(new PoissonDistribution(1.0));
		}
		this.regression = ensemble.get(0).isRegression();
		this.summarizer = ResultSummarizer.getSummarizer(regression, schema);
		log.info("created ensemble of size {}",n_learners);
	}
	
	@Override
	public void update(Instance instance) {

		log.debug("OnlineBagging.train, instance={}, n_learners={}", instance, n_learners);
		if (instance==null)
			return;

		double label = instance.getLabel();
		if (label == -1)
			log.error("skipping instance={}, no label found",instance);

		// update summary
		double predicted = predict(instance);
		summarizer.add(predicted, label);

		for (int i=0;i<n_learners;i++) {
			Learner learner = ensemble.get(i);
			int k = poisson.get(i).sample();
			
			for (int j=0;j<k;j++)
				learner.update(instance);

            // TODO improve OOB estimate by doing full_predict over all trees with k==0
            if (k==0) {
				predicted = learner.predict(instance);
				double actual = instance.get(schema.getAttributeIndex(target));
				summarizer.add(predicted, actual);
			}
		}
	}

	public double predict(Instance instance) {
        if (instance == null)
			return Double.NaN;

		if (regression) {
            // unweighted average
			double sum = 0;
			int cnt = 0;
			for (Learner l: ensemble) {
				double prediction = l.predict(instance);
				if (!Double.isNaN(prediction)) {
					sum += prediction;
					cnt++;
				}
			}
			return (cnt>0) ? (sum/cnt) : Double.NaN;
		}
		else
            // majority vote
			return DataUtils.maxindex(predictFull(instance));
	}
	 
	public double[] predictFull(Instance instance) {
		if (regression)
			throw new FeaturestreamException("predictFull not supported on regression learner");
		
		// get predictions
		double[][] predictions = new double[ensemble.size()][];
		int ix=0,s=0;
		for (Learner l: ensemble) {
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
        // TODO weight by OOB error
		double sum=0;
		for (int i=0;i<prediction.length;i++)
			sum+=prediction[i];		
		if (sum > 0)
			for (int i=0;i<prediction.length;i++)
				prediction[i]/=sum;
		return prediction;
	}
	
	public Vector transform(Instance instance) {
		// sum the transform vectors over the ensemble
		List<Vector> L = new ArrayList<Vector>(n_learners);
		int sum_size=0;
		int max_size=0;
		for (Learner l : ensemble) {
			Vector v = l.transform(instance);
			max_size = Math.max(v.size(),max_size);
			sum_size += v.size();
			L.add(v);
		}
		Vector v = null;
		final boolean concat = false;
		if (concat) {
			// concatenate vectors
			v = new RandomAccessSparseVector(sum_size);
			int offset = 0;
			for (Vector x : L) {
				for (Element e: x.nonZeroes()) {
					v.set(offset+e.index(),e.get());
				}
				offset += x.size();
			}
		} else {
			// sum vectors
			// better sparse distributed representation?
			v = new RandomAccessSparseVector(max_size);
			for (Vector x : L)
				v.assign(x, Functions.PLUS);			
		}
		return v;
	}

    // model-level feature importances
	public Vector featureImportances() {
		// average the feature importances over the ensemble
		Vector importances = ensemble.get(0).featureImportances().like();
		for (Learner l : ensemble)
			importances.assign(l.featureImportances(), Functions.PLUS);
		if (importances.size() == 0)
			return importances;
		double sum = importances.aggregate(Functions.PLUS, Functions.IDENTITY);
		return (sum > 0.0) ? importances.assign(Functions.div(sum)) : importances;
	}

    // instance-level feature importances
	public Vector featureImportances(Instance instance) {
		// average the feature importances over the ensemble
		Vector importances = ensemble.get(0).featureImportances(instance).like();
		for (Learner l : ensemble)
			importances.assign(l.featureImportances(instance), Functions.PLUS);
		if (importances.size() == 0)
			return importances;
		double sum = importances.aggregate(Functions.PLUS, Functions.IDENTITY);
		return (sum > 0.0) ? importances.assign(Functions.div(sum)) : importances;
	}

	@Override
	public Stats getStats() {
		Stats stats = summarizer.getSummary();
		stats.add("n_models",ensemble.size());
		return stats;
	}
	
	@Override
	public void clearStats() {
		summarizer.clear();
	}

	@Override
	public boolean isRegression() {
		return regression;
	}

	@Override
	public Stats getInfo() {
		Stats stats = new Stats();
		stats.add("type", this.getClass().getName());
		stats.add("n_learners", n_learners);
		Stats ensemble_stats = new Stats();
		int i=0;
		for (Learner l : ensemble)
			ensemble_stats.add(Integer.toString(i++), l.getInfo());

		stats.add("ensemble", ensemble_stats);
		stats.add("summary", getStats());
		stats.add("feature_importances", schema.unmapVectorIndexes(featureImportances(),true));
		stats.add("feature_importances_full", schema.unmapVectorIndexes(featureImportances(),false));
		return stats;
	}

	@Override
	public Schema getSchema() {
		return schema;
	}

}
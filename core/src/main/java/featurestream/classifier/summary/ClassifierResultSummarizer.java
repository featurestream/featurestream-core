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

package featurestream.classifier.summary;

import featurestream.data.schema.Schema;
import featurestream.utils.Stats;

public class ClassifierResultSummarizer extends ResultSummarizer {

	double n_total;
	double n_correct;
	ConfusionMatrix matrix;
	OnlineExponentialAverage exp_avg;
	Schema schema;
//	OnlineAuc auc; // mahout Matrix not serializable FIXME
//	ADWIN adwin;

	public ClassifierResultSummarizer(Schema schema){
		this.schema = schema;
		clear();
	}

	public double getErrorEstimate() {
		return accuracy();
	}
	
	public void add(double[] predicted, double actual) {
		
		
	}

	public void add(double predicted, double actual) {
		if (Double.isNaN(actual))
			return;
		
		boolean correct = (predicted==actual);
		if (correct)
			n_correct += 1;
		n_total += 1;
		Object correctLabel = schema.unmapAttribute(schema.getTarget(), actual);
		Object predictedLabel = schema.unmapAttribute(schema.getTarget(), predicted);
		matrix.addInstance((correctLabel==null) ? null : correctLabel.toString(), (predictedLabel==null) ? null : predictedLabel.toString());
		exp_avg.add(correct ? 1 : 0);
//		if (schema.hasAttribute(target) && schema.getAttribute(target).nValues() == 2)
//			auc.addSample((int)actual, predicted);
//		adwin.setInput(correct ? 1 : 0);
	}
	
	public double auc() {
		return -1;
//		return auc.auc();
	}
	
	public double accuracy() {
		return (n_total > 0.0) ? n_correct / n_total : 0.0;
	}

/*	// loglikelihood measure in [-100, 0] and closer to 0 indicates better accuracy
	public double logLikelihood(int actual, Vector predictions) {
		if (numCategories() == 2) {
			double p = classifyScalar(data);
			if (actual > 0) {
				return Math.max(-100.0, Math.log(p));
			} else {
				return Math.max(-100.0, Math.log1p(-p));
			}
		} else {
			Vector p = classify(data);
			if (actual > 0) {
				return Math.max(-100.0, Math.log(p.get(actual - 1)));
			} else {
				return Math.max(-100.0, Math.log1p(-p.zSum()));
			}
		}
	}
*/
	public Stats getSummary() {
		Stats stats = new Stats();
		if (schema.getAttribute(schema.getTarget()).nValues() == 2)
			stats.add("auc", auc());
		stats.add("accuracy",accuracy());
		stats.add("exp_accuracy",exp_avg.estimate());
		stats.add("n_correct",n_correct);
		stats.add("n_total",n_total);
		stats.add("confusion", matrix.getMap());
		stats.add("scores", matrix.getScores());
		stats.add("type", "classification");
//		stats.add("adwin", adwin.getErrorEstimate());
//		stats.add("adwin_detections", adwin.getNumberDetections());
		return stats;
	}

	@Override
	public void clear() {
		n_total=0;
		n_correct=0;
		matrix = new ConfusionMatrix();
		exp_avg = new OnlineExponentialAverage();
//		auc = new OnlineAuc();
//		adwin = new ADWIN();
	}

	@Override
	public ResultSummarizer merge(ResultSummarizer summarizer) {
		ClassifierResultSummarizer crs = (ClassifierResultSummarizer) summarizer;
		n_correct += crs.n_correct;
		n_total += crs.n_total;
		matrix = matrix.merge(crs.matrix);
		exp_avg = exp_avg.merge(crs.exp_avg);
//		auc = auc.merge(crs.auc);
		return this;
	}
	
}

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

import featurestream.utils.Stats;

public class RegressionResultSummarizer extends ResultSummarizer {

	double sumActual;
	double sumActualSquared;
	double sumResult;
	double sumResultSquared;
	double sumActualResult;
	double sumAbsolute;
	double sumAbsoluteSquared;
	int predictable;
	int unpredictable;
	OnlineExponentialAverage exp_avg;

	public RegressionResultSummarizer() {
		clear();
	}

	public double getErrorEstimate() {
		// rmse
		return (predictable > 0.0) ? Math.sqrt(sumAbsoluteSquared / predictable) : 0.0;
	}

	public void add(double predicted, double actual) {
		if (Double.isNaN(actual))
			return;

		if (Double.isNaN(predicted))
			unpredictable++;
		else {
			sumActual += actual;
			sumActualSquared += actual * actual;
			sumResult += predicted;
			sumResultSquared += predicted * predicted;
			sumActualResult += actual * predicted;
			double absolute = Math.abs(actual - predicted);
			sumAbsolute += absolute;
			sumAbsoluteSquared += absolute * absolute;
			predictable++;
			exp_avg.add(absolute);
		}
	}

	public Stats getSummary() {
		Stats stats = new Stats();

		double correlation = 0;
		if (predictable > 0) {
			double varActual = sumActualSquared - sumActual * sumActual / predictable;
			double varResult = sumResultSquared - sumResult * sumResult / predictable;
			double varCo = sumActualResult - sumActual * sumResult /  predictable;

			if (varActual * varResult > 0)
				correlation = varCo / Math.sqrt(varActual * varResult);

			stats.add("correlation_coefficient",correlation);
			stats.add("mean_abs_error",sumAbsolute/predictable);
			stats.add("rmse",Math.sqrt(sumAbsoluteSquared / predictable));
			stats.add("exp_rmse",exp_avg.estimate());
		}
		stats.add("n_predictable",predictable);
		stats.add("n_unpredictable",unpredictable);
		stats.add("n_total",predictable+unpredictable);
		stats.add("type", "regression");
		return stats;
	}

	@Override
	public void clear() {
		sumActual = 0.0;
		sumActualSquared = 0.0;
		sumResult = 0.0;
		sumResultSquared = 0.0;
		sumActualResult = 0.0;
		sumAbsolute = 0.0;
		sumAbsoluteSquared = 0.0;
		predictable = 0;
		unpredictable = 0;
		exp_avg = new OnlineExponentialAverage();
	}

	@Override
	public ResultSummarizer merge(ResultSummarizer summarizer) {
		RegressionResultSummarizer rrs = (RegressionResultSummarizer) summarizer;
		sumActual += rrs.sumActual;
		sumActualSquared += rrs.sumActualSquared;
		sumResult += rrs.sumResult;
		sumResultSquared += rrs.sumResultSquared;
		sumActualResult += rrs.sumActualResult;
		sumAbsolute += rrs.sumAbsolute;
		sumAbsoluteSquared += rrs.sumAbsoluteSquared;
		predictable += rrs.predictable;
		unpredictable += rrs.unpredictable;
		exp_avg.merge(rrs.exp_avg);
		return this;
	}
}
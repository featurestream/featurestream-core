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

import java.io.Serializable;

/*
 * 
 * an interfaces for classes to accumulate predicted vs actual results and to
 * compute various statistics
 */
public abstract class ResultSummarizer implements Summarizer, Serializable {
	
	// accumulate a predicted vs actual result
	public abstract void add(double predicted, double actual);
	// clear stats
	public abstract void clear();
	
	public abstract ResultSummarizer merge(ResultSummarizer summarizer);
	
//	public abstract double getErrorEstimate();
	
	public static ResultSummarizer getSummarizer(boolean regression, Schema schema) {
		if (regression)
			return new RegressionResultSummarizer();
		else
			return new ClassifierResultSummarizer(schema);
	}
}

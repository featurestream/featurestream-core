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

package featurestream.data.schema;

import featurestream.data.Event;

public class NumericAttribute extends Attribute {
	private static final long serialVersionUID = 1L;

	double sum_sq;
	double sum;
	double count;
	double max;
	double min;
	final double EPSILON = 1e-6;
	
	public NumericAttribute() {
		super(Event.Entry.Type.NUMERIC);
		
		sum_sq=0.0;
		sum=0.0;
		count=0.0;
		max=Double.MIN_VALUE;
		min=Double.MAX_VALUE;
	}

	@Override
	public int nValues() {
		return -1;
	}
	
	// feature scaling to mean 0, unit variance
	public double normalize(double v) {
		double mu = sum/count;
		double var = sum_sq/count - mu*mu;
		double w = (count==0 || var < EPSILON) ? 0.0 : (v-mu)/Math.sqrt(var);
		return w;
	}
	
	// rescale to mean mu, variance var
	public double denormalize(double v) {
		double mu = sum/count;
		double var = sum_sq/count - mu*mu;
		double w = (count==0 || var < EPSILON) ? 0.0 : v*Math.sqrt(var) + mu;
		return w;
	}

	@Override
	public Double unmapValue(Double v) {
		return v;
	}
	
	public Double mapValue(Number value) {
		double v = value.doubleValue();
		sum_sq+=v*v;
		sum+=v;
		count++;
		max=Math.max(max,v);
		min=Math.min(min,v);
//		return normalize(v); // don't normalize here (eg clustering normalizes, and can't normalize twice)
		return v;	
	}

	public Double mapValue(String value) {
		// try to parse as double
		return mapValue(Double.valueOf(value));
	}

	public String toString() {
		double mu = sum/count;
		double var = sum_sq/count - mu*mu;
		return "NumericAttribute [type=" + type + 
				", count="+count+
				", mean="+mu+
				", var="+var+"]";
	}

	public Attribute merge(Attribute value) {
		NumericAttribute a = (NumericAttribute) value;
		sum_sq+=a.sum_sq;
		sum+=a.sum;
		count+=a.count;
		max=Math.max(max,a.max);
		min=Math.max(min,a.min);
		return this;
	}

}

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
import featurestream.utils.MurmurHash3;

import java.util.Arrays;
import java.util.Random;

public class HashedAttribute extends Attribute {
	private static final long serialVersionUID = 1L;

	final int range;// prime < HISTOGRAM_BUCKETS/2 (java mod has sign of dividend)
	final int seed;
	final static int[] PRIMES = new int[]{2,3,5,7,11,13,17,19,23,29,31,37,41,43,47,53,59,61,67,71 
								,73,79,83,89,97,101,103,107,109,113 
								,127,131,137,139,149,151,157,163,167,173 
								,179,181,191,193,197,199,211,223,227,229 
								,233,239,241,251,257,263,269,271,277,281 
								,283,293,307,311,313,317,331,337,347,349 
								,353,359,367,373,379,383,389,397,401,409 
								,419,421,431,433,439,443,449,457,461,463 
								,467,479,487,491,499,503,509,521,523,541};
	final static int MAX_PRIME = 541;
	
	@Override
	public String toString() {
		return "HashedAttribute [type=" + type + ", range="+range+"]";
	}

	public int nValues() {
		return range;// in [1,range) TODO use FM sketch
	}
	
	public String unmapValue(Double v) {
		return null; // doesn't make sense to unmap it any other way
	}
	
	public Double mapValue(Number value) {
		return mapValue(value.toString());
	}
	
	public Double mapValue(String value) {
		// map into [1,range]
		Double d = new Double(1+Math.abs(MurmurHash3.murmurhash3_x86_32(value, 0, value.length(), seed) % range));
		assert (d!=0);
		return d;
	}
	
	public HashedAttribute(int k) {
		super(Event.Entry.Type.CATEGORIC);
		assert (k>0);
		if (k < MAX_PRIME)
			range = PRIMES[Arrays.binarySearch(PRIMES,k)];
		else
			range = k;
		this.seed = (new Random()).nextInt();
	}
	
	public HashedAttribute() {
		this(43);
	}

	public Attribute merge(Attribute value) {
		// nothing to do
		return this;
	}

}

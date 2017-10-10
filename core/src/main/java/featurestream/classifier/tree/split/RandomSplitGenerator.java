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
package featurestream.classifier.tree.split;

import com.bigml.histogram.Histogram;
import featurestream.data.schema.Attribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomSplitGenerator implements SplitGenerator {
	
	final int NUM_SPLITS;
	Random rng;
    static boolean TREAT_ALL_AS_NUMERIC = false;

	public RandomSplitGenerator(int NUM_SPLITS) {
		this.NUM_SPLITS = NUM_SPLITS;
		this.rng = new Random();
	}

	/**
	 * return a random split on attr without using the histogram.uniform if numeric, and a random value if categoric
	 * 
	 * @param attr attribute to consider splitting on
	 * @return
	 */
	public List<Split> candidateSplits(Attribute attr, int ix, Histogram h) {

		List<Split> splits = new ArrayList<>();
		if (TREAT_ALL_AS_NUMERIC || attr.isNumeric()) {
			// numeric binary split
			ArrayList<Double> uniform = h.uniform(NUM_SPLITS);
			splits.add(new Split(ix,Double.NaN,uniform.get(rng.nextInt(uniform.size())),Double.NaN,true));
		} else
			// categoric split
			splits.add(new Split(ix,Double.NaN,Double.NaN,Double.NaN,false));

		return splits;
	}

}

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
import com.bigml.histogram.Target;

import java.io.Serializable;

public interface SplitEvaluator extends Serializable {

	public Split evaluateSplit(Split split, Histogram<Target> h);

}

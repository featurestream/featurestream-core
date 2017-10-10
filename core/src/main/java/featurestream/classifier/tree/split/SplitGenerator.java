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

import java.io.Serializable;
import java.util.List;

public interface SplitGenerator extends Serializable {

	public List<Split> candidateSplits(Attribute attr, int ix, Histogram h);

}

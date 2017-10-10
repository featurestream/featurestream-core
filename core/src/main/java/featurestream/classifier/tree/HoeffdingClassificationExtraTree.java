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

package featurestream.classifier.tree;

import featurestream.classifier.tree.split.RandomSplitGenerator;
import featurestream.data.schema.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class HoeffdingClassificationExtraTree extends HoeffdingClassificationTree {

	private static final Logger log = LoggerFactory.getLogger(HoeffdingClassificationExtraTree.class);	

	public HoeffdingClassificationExtraTree(Schema schema, String target, Properties props) {
		super(schema, target, props, 
				new RandomSplitGenerator(NUM_SPLITS));
	}
}

	  

/* 
 * Copyright (C) 2013 Andrew Twigg - All Rights Reserved
 * Unauthorized copying or distribution 
 * of this file, via any medium, is strictly prohibited
 * Proprietary and confidential.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package featurestream.classifier;


import featurestream.classifier.rf.RF;
import featurestream.classifier.tree.HoeffdingClassificationExtraTree;
import featurestream.classifier.tree.HoeffdingClassificationTree;
import featurestream.classifier.tree.HoeffdingRegressionExtraTree;
import featurestream.classifier.tree.HoeffdingRegressionTree;
import featurestream.data.Event;
import featurestream.data.schema.Schema;
import featurestream.utils.FeaturestreamException;

import java.util.Properties;

public class LearnerFactory {
	
	public enum LearnerType {
		hoeffding_classifier,
		hoeffding_regressor,
		hoeffding_extra_classifier,
		hoeffding_extra_regressor,
		rf_classifier,
		rf_regressor,
		rf_extra_classifier,
		rf_extra_regressor
	}
	
	public static LearnerType getLearnerTypeFromTargetType(Event.Entry.Type type) {
		switch (type) {
		case CATEGORIC:
			return LearnerType.rf_classifier;
		case NUMERIC:
			return LearnerType.rf_regressor;
		case TEXT:
		case DATETIME:
		default:
			throw new FeaturestreamException("target type="+type+" not supported");
		}
	}
	
	public static Learner getLearner(Schema schema, String target, LearnerType type, Properties props) {
		switch (type) {
		case hoeffding_classifier:
			return new HoeffdingClassificationTree(schema,target,props);
		case hoeffding_regressor:
			return new HoeffdingRegressionTree(schema,target,props);
		case hoeffding_extra_classifier:
			return new HoeffdingClassificationExtraTree(schema,target,props);
		case hoeffding_extra_regressor:
			return new HoeffdingRegressionExtraTree(schema,target,props);
		case rf_classifier:
			return new RF(schema,target,LearnerType.hoeffding_classifier,props);
		case rf_regressor:
			return new RF(schema,target,LearnerType.hoeffding_regressor,props);
		case rf_extra_classifier:
			return new RF(schema,target,LearnerType.hoeffding_extra_classifier,props);
		case rf_extra_regressor:
			return new RF(schema,target,LearnerType.hoeffding_extra_regressor,props);
		default:
			return null;
		}
	}
}

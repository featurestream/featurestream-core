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

import com.bigml.histogram.*;
import featurestream.utils.FeaturestreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class RegressionSplitEvaluator implements SplitEvaluator {

	private static final Logger log = LoggerFactory.getLogger(RegressionSplitEvaluator.class);

	public Split evaluateSplit(Split split, Histogram h) {
		
		log.debug("evaluating split={}, histogram={}",split,h);
		if (split.is_numeric) {
			try {
				// see (9) in [Parallel Boosted Regression Trees, Tyree et al.]
				// G = \sum_{s in {left,right}} \sum_{(x_i,y_i) in L_s} (y_i - l_s/m_s)^2
				// l_s = sum_{(x_i,y_i) in L_s} y_i, m_s = |L_s|
				NumericTarget t_inf = (NumericTarget) h.getTotalTargetSum();
				double l_inf = t_inf.getSum();
				double m_inf = h.getTotalCount();
				
				SumResult<NumericTarget> sr = h.extendedSum(split.split);
				NumericTarget t = sr.getTargetSum();
				double l = t.getSum();
				double m = sr.getCount();
				log.debug("l_inf={},m_inf={},l={},mtry={},t={}",l_inf,m_inf,l,m,t);
				split.tau = m/m_inf;
				split.G = (split.tau==1.0) ? 0.0 : (l*l/m + ((l_inf - l)*(l_inf - l))/(m_inf - m)); // otherwise G==NaN

			} catch (SumOutOfRangeException e) { log.warn("split={}",split); throw new FeaturestreamException(e); }
		}
		else {
			// categorical split
			// G = \sum_{s=0..n_values} \sum_{(x_i,y_i) in L_s} (y_i - l_s/m_s)^2
			// l_s = sum_{(x_i,y_i) in L_s} y_i, m_s = |L_s|

			NumericTarget t_inf = (NumericTarget) h.getTotalTargetSum();
			double l_inf = t_inf.getSum();
			double m_inf = h.getTotalCount();

			
			double G = 0;
			double best_i = -1;
			double best_gain = -1;
			double best_m = -1;
			double best_l = -1;
			for (Bin<NumericTarget> bin: (Collection<Bin>) h.getBins()) {
				double i = bin.getMean();
				NumericTarget target = bin.getTarget();
				double l = target.getSum();
				double m = bin.getCount();
				log.debug("i={},target={},l={},mtry={}",i,target,l,m);
//				G += l*l/mtry;
				double gain_i = l*l/m;
				if (gain_i > best_gain) {
					best_gain = gain_i;
					best_i = i;
					best_l = l;
					best_m = m;
				}
			}
			
			// pick best value
			if (Math.floor(best_i) != best_i)
				log.warn("bins have shifted, split estimate might not be accurate. bin={}, histogram={}",best_i,h);
			log.debug("l_inf={}, best_l={}, m_inf={}, best_m={}",l_inf,best_l,m_inf,best_m);
			boolean empty = Math.abs(l_inf-best_l) < 1e-6;
			split.G = best_gain + (empty ? 0 : (l_inf-best_l)*(l_inf-best_l)/(m_inf-best_m));
			split.split = Math.floor(best_i);
			split.tau = (best_m/m_inf);
			
		}
		
		assert (!Double.isNaN(split.G));
		log.debug("split={}, tau={}, Delta={}",split, split.tau, split.G);

		return split;
	}

}

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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class IgSplitEvaluator implements SplitEvaluator {

	private static final Logger log = LoggerFactory.getLogger(IgSplitEvaluator.class);	

	public Split evaluateSplit(Split split, Histogram<Target> h) {

		if (split.is_numeric) {

			// G(leaf) = entropy([h(attr,j).sum(inf) for j in labels)
			// G(left) = entropy([h(attr,j).sum(split) for j in labels])
			// G(right) = entropy([h(attr,j).sum(inf) - h(attr,j).sum(split) for j in labels])
			// tau = fraction of examples going left = h(attr).sum(v) / h(attr).sum(inf)
			// Delta = G(leaf) - tau.G(left) - (1-tau).G(right)
			try {
				CategoricalTarget target = (CategoricalTarget) h.getTotalTargetSum();
				Map<Object,Double> leaf_counts = target.getCounts();
				double G = Entropy.entropy(leaf_counts);

				SumResult sr = h.extendedSum(split.split);
				double tau = sr.getCount() / h.getTotalCount();
				target = (CategoricalTarget) sr.getTargetSum();
				Map<Object,Double> left_counts = target.getCounts();
				double G_left = Entropy.entropy(left_counts);

				// FIXME: don't allocate new map just to compute entropy of its values
				Map<Object,Double> right_counts = new HashMap<Object,Double>();
				for (Entry<Object,Double> e : leaf_counts.entrySet())
					right_counts.put(e.getKey(), e.getValue() - (left_counts.containsKey(e.getKey()) ? left_counts.get(e.getKey()) : 0.0));
				double G_right = Entropy.entropy(right_counts);

				split.G = G - tau*G_left - (1.0-tau)*G_right; // want to maximize the entropy gain
				split.tau = tau;

			} catch (SumOutOfRangeException e) { log.warn("split={}",split); throw new FeaturestreamException(e); }
		}
		else {
			// categorical split
			CategoricalTarget target = (CategoricalTarget) h.getTotalTargetSum();
			Map<Object,Double> leaf_counts = target.getCounts();
			double total = h.getTotalCount();
			double G_leaf = Entropy.entropy(leaf_counts);
			double G = G_leaf;
			
			


			
			// TODO can stop early if we find a value that takes > 1/2 entropy gain
//			OpenIntDoubleHashMap map = new OpenIntDoubleHashMap(h.getMaxBins());
			double best_gain = Double.MAX_VALUE;
			double best_i = -1;
			double best_m = -1;
			for (Bin bin: (Collection<Bin<Target>>) h.getBins()) {			
				double i = bin.getMean();
				target = (CategoricalTarget) bin.getTarget();
				Map<Object,Double> counts = target.getCounts();
				double G_i = Entropy.entropy(counts);
				double m_i = bin.getCount();
//				log.debug("i={},target={},counts={},mtry={}",i,target,counts,mtry);
				double gain_i = (m_i/total)*G_i;
//				G -= gain_i;
				
				if (gain_i < best_gain) {
					best_i = i;
					best_gain = gain_i;
					best_m = m_i;
				}
				//map.put((int)i, gain_i);
			}
			
			// take the best value
			if (Math.floor(best_i) != best_i)
				log.warn("bins have shifted, split estimate might not be accurate, bin={}, histogram={}",best_i,h);
			// TODO don't split if shift detected?
			split.split = Math.floor(best_i);
			log.debug("best_m={}, total={}",best_m,total);
			split.G = G_leaf*(1.0-best_m/total) - best_gain; // approximate G_* <= G_leaf
			split.tau = best_m / total;

/*			// take the deg-1 attributes with the smallest (mtry/total)*G_i
			G = G_leaf;
			NumericTarget t_inf = (NumericTarget) h.getTotalTargetSum();
			IntArrayList keyList = new IntArrayList(map.size()); 
			DoubleArrayList valueList = new DoubleArrayList(map.size()); 
			map.pairsSortedByValue(keyList, valueList);
			int l = keyList.size();
			int s = Math.min(deg-1,l);// number of values to include in the split
			int[] vals = new int[s];
			for (int i=0;i<s;i++) {
				G -= valueList.getQuick(i);
				vals[i] = keyList.getQuick(i);
			}
			// and whatever remains in the wildcard
			// use G_* <= G_leaf approximation
//			G -= G_leaf*(m_i in vals/total); // TODO
			split.G = G;
			split.vals = vals;
*/
		}

		return split;

	}
}

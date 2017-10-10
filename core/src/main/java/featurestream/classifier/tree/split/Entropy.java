
/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package featurestream.classifier.tree.split;


import featurestream.utils.DataUtils;

import java.util.Map;

/**
 * Optimized implementation of IgSplit<br>
 * This class can be used when the criterion variable is the categorical attribute.
 */
public class Entropy {

  /**
   * Computes the Entropy
   *
   * @param counts   counts[i] = numInstances with label i
   * @param dataSize numInstances
   */
  public static double entropy(int[] counts, int sum) {
    if (sum == 0) {
      return 0;
    }

    double entropy = 0.0;
    double invDataSize = 1.0 / sum;

    for (int count : counts) {
      if (count == 0) {
        continue; // otherwise we get a NaN
      }
      double p = count * invDataSize;
      entropy += -p * Math.log(p) / Math.log(2);
    }

    return entropy;
  }
  
  public static double entropy(int[] counts) {
	  return entropy(counts, DataUtils.sum(counts));
  }
  
  public static <K> double entropy(Map<K,Double> counts) {
	  double sum=0;
	  for (Double d:counts.values())
		  sum += d;
	  
	  if (sum==0)
		  return 0.0;
	  
	  double entropy = 0.0;
	  double invDataSize = 1.0 / sum;

	  for (Double count : counts.values()) {
		  if (count == 0.0) {
			  continue; // otherwise we get a NaN
		  }
		  double p = count * invDataSize;
		  entropy += -p * Math.log(p) / Math.log(2);
	  }

	  return entropy;
	  
  }

  /**
   * Computes the Entropy
   *
   * @param counts   counts[i] = numInstances with label i
   * @param dataSize numInstances
   */
  public static double entropy(double[] counts, double sum) {
    if (sum == 0) {
      return 0.0;
    }

    double entropy = 0.0;
    double invDataSize = 1.0 / sum;

    for (double count : counts) {
      if (count == 0.0) {
        continue; // otherwise we get a NaN
      }
      double p = count * invDataSize;
      entropy += -p * Math.log(p) / Math.log(2);
    }

    return entropy;
  }
  
  public static double entropy(double[] counts) {
	  return entropy(counts, DataUtils.sum(counts));
  }
}

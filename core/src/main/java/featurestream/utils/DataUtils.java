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

package featurestream.utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.mahout.math.Vector;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Helper methods that deals with data lists and arrays of values
 */
public final class DataUtils {
  private DataUtils() { }
  
  /** 
   * convert mahout vector to array
   * @param v
   * @return
   */
	public static double[] toArray(Vector v) {
		double[] components = new double[v.size()];
		for (int i = 0; i < components.length; i++)
			components[i] = v.getQuick(i);
		return components;
	}

  /**
   * Computes the sum of the values
   * 
   */
  public static int sum(int[] values) {
    int sum = 0;
    for (double value : values) {
      sum += value;
    }
    
    return sum;
  }
  
  /**
   * Computes the sum of the values
   * 
   */
  public static double sum(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    
    return sum;
  }

    public static int nnz(int[] values) {
        int nnz=0;
        for (int value: values) {
            if (value != 0)
                nnz++;
        }
        return nnz;
    }
  
  /**
   * foreach i : array1[i] += array2[i]
   */
  public static void add(int[] array1, int[] array2) {
    Preconditions.checkArgument(array1.length == array2.length, "array1.length != array2.length");
    for (int index = 0; index < array1.length; index++) {
      array1[index] += array2[index];
    }
  }
  
  /**
   * foreach i : array1[i] -= array2[i]
   */
  public static void dec(int[] array1, int[] array2) {
    Preconditions.checkArgument(array1.length == array2.length, "array1.length != array2.length");
    for (int index = 0; index < array1.length; index++) {
      array1[index] -= array2[index];
    }
  }
  
  /**
   * return the index of the maximum of the array, breaking ties randomly
   * 
   * @param rng
   *          used to break ties
   * @return index of the maximum
   */
  public static int maxindex(Random rng, int[] values) {
    int max = 0;
    List<Integer> maxindices = Lists.newArrayList();
    
    for (int index = 0; index < values.length; index++) {
      if (values[index] > max) {
        max = values[index];
        maxindices.clear();
        maxindices.add(index);
      } else if (values[index] == max) {
        maxindices.add(index);
      }
    }

    return maxindices.size() > 1 ? maxindices.get(rng.nextInt(maxindices.size())) : maxindices.get(0);
  }

  /**
   * return the index of the maximum of the array, breaking ties to the left
   * 
   * @return index of the maximum
   */
  public static int maxindex(int[] values) {
		int ix=-1, max=Integer.MIN_VALUE;
		for (int i=0;i<values.length;i++) {
			if (values[i]>max) {
				max = values[i];
				ix = i;
			}
		}
		return ix;
  }
  
  public static int maxindex(double[] values) {
		int ix=-1;
		double max=Double.NEGATIVE_INFINITY;
		for (int i=0;i<values.length;i++) {
			if (values[i]>max) {
				max = values[i];
				ix = i;
			}
		}
		return ix;
  }

  public static <E> Iterable<E> makeIterable(final Iterator<E> iterator) {
      if (iterator == null) {
          throw new NullPointerException();
      }
      return new Iterable<E>() {
          public Iterator<E> iterator() {
              return iterator;
          }
      };
  }


}

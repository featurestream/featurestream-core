
/*
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

package featurestream.classifier.summary;

import java.io.Serializable;

public class OnlineExponentialAverage implements Serializable {

	CountExpAverage[] avgs;
	int k;
	
	public OnlineExponentialAverage() {
		this(5); // alpha=1.0, 0.1, 0.01, 0.001
	}

	public OnlineExponentialAverage(int k) {
		this.k = k;
		avgs = new CountExpAverage[k];
		for (int i=0;i<k;i++)
			avgs[i] = new CountExpAverage(Math.pow(2.0,-i));
	}

	public void add(double x) {
		if (Double.isNaN(x))
			return;
		for (CountExpAverage avg : avgs)
			avg.add(x);
	}

	public double[] estimate() {
		double[] estimates = new double[k];
		for (int i=0;i<k;i++)
			estimates[i] = avgs[i].estimate();
		return estimates;
	}


	/**
	 * Computes an online average that is exponentially weighted toward recent samples.
	 * alpha = 1.0 is long-term average
	 * as alpha approaches 0.0, it weights towards recent observations
	 */
	class CountExpAverage implements Serializable {

		private final double alpha;
		private double s;
		private int count;

		public CountExpAverage(double alpha) {
			this.alpha = Math.max(Math.min(1.0,alpha),0.0);
			s = 0.0;
			count = 0;
		}

		public void add(double x) {
			count++;
			double beta = 1.0/(alpha*count);
			s = beta*x + (1.0-beta)*s;
		}

		public double estimate() {
			return s;
		}
		
		public CountExpAverage merge(CountExpAverage in) {
			this.s = ((this.count*this.s)+(in.count*in.s))/(this.count+in.count);
			this.count += in.count;
			return this;
		}

	}

	/**
	 * Computes an online average that is exponentially weighted toward recent samples.
	 * as alpha approaches 0.0, it weights towards recent observations
	 */
	class ExpAverage {
		private final double alpha;
		private double s;

		public ExpAverage(double alpha) {
			this.alpha = 1.0 - Math.max(Math.min(1.0,alpha),0.0); // make it comparable to CountExpAverage
			s = 0.0;
		}

		public void add(double x) {
			s = alpha*x + (1.0-alpha)*s;
		}

		public double estimate() {
			return s;
		}
	}

	public OnlineExponentialAverage merge(OnlineExponentialAverage in) {
		assert (this.k == in.k);
		for (int i=0;i<k;i++)
			this.avgs[i].merge(in.avgs[i]);
		return this;
	}

}
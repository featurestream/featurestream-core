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

public class Split implements Comparable<Split> {
	@Override
	public String toString() {
		return "Split [attr=" + attr + ", G=" + G + ", split=" + split
				+ ", tau=" + tau + ", is_numeric=" + is_numeric + "]";
	}
	public Split(int attr, double g, double split, double tau,
			boolean is_numeric) {
		this.attr = attr;
		G = g;
		this.split = split;
		this.tau = tau;
		this.is_numeric = is_numeric;
	}
	  
	public int attr;
	public double G;
	public double split;
	public double tau;
	public boolean is_numeric;

	public int compareTo(Split other) {
		return Double.compare(this.G, other.G);
	}
}

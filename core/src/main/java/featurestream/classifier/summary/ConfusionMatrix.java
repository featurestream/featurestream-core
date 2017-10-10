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

/**
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * Licensed to the Apache Software Foundation (ASF) under one or more
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


import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import featurestream.utils.Stats;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * See http://en.wikipedia.org/wiki/Confusion_matrix 
 */
public class ConfusionMatrix implements Serializable {
	private final Map<String,Multiset<String>> matrix;
	private Multiset<String> rowTotals;
	private Multiset<String> colTotals;

	public ConfusionMatrix() {
		matrix = new HashMap<String,Multiset<String>>();
		rowTotals = HashMultiset.create(2);
		colTotals = HashMultiset.create(2);
	}
	
	public Map<String,Map<String,Integer>> getMap() {
		Map<String,Map<String,Integer>> map = new HashMap<String,Map<String,Integer>>();
		for (Map.Entry<String, Multiset<String>> row: matrix.entrySet()) {
			Map<String,Integer> m = new HashMap<String,Integer>();
			for (String e : row.getValue().elementSet())
				m.put(e,row.getValue().count(e));		
			map.put(row.getKey(),m);
		}
		return map;
	}
	public Collection<String> getLabels() {
		return matrix.keySet();
	}
	
	public double getAccuracy(String label) {
		return ((double)getCorrect(label)) / getTotal(label);
	}

	public int getCorrect(String label) {
		Multiset<String> row = getRow(label);
		return row.count(label);
	}

	public int getTotal(String label) {
		Multiset<String> row = getRow(label);
		return row.size();
	}
	
	private Multiset<String> getRow(String label) {
		Multiset<String> row = matrix.get(label);
		if (row == null) {
			row = HashMultiset.create(2);
			matrix.put(label,row);
		}
		return row;
	}
	
	public void addInstance(String correctLabel, String classifiedLabel) {		
		if (classifiedLabel==null)
			classifiedLabel="?";
		if (correctLabel==null)
			correctLabel="?";

		Multiset<String> row = getRow(correctLabel);
		row.add(classifiedLabel);
		rowTotals.add(correctLabel);
		colTotals.add(classifiedLabel);
	}

	public int getCount(String correctLabel, String classifiedLabel) {
		Multiset<String> row = getRow(correctLabel);
		return row.count(classifiedLabel);
	}
	
	public Map<String,Object> getScores() {
		Collection<String> labels = getLabels();
		Map<String,Object> map = new HashMap<String,Object>();
//		double diagonal_sum=0.0;
		// per-label
		for (String l: labels) {
			double c = getCount(l,l);
			double precision = c / rowTotals.count(l);
			double recall = c / colTotals.count(l);
			double f1 = 2.0*precision*recall/(precision+recall);
//			diagonal_sum += getCount(l,l);
			Stats stats = new Stats();
			stats.add("precision", precision);
			stats.add("recall", recall);
			stats.add("F1", f1);
			map.put(l, stats.getStats());
		}
		// total
		// duplicated elsewhere (accuracy)
//		double precision = diagonal_sum / rowTotals.sum();
//		map.put("_total_", precision);
		
		return map;	
	}
	
	public ConfusionMatrix merge(ConfusionMatrix cm) {
		System.out.println("merging confusion matrix:");
		System.out.println("this="+this.getMap());
		System.out.println("in="+cm.getMap());
		
		// elementwise sum
		for (Map.Entry<String, Multiset<String>> row: cm.matrix.entrySet()) {
			Multiset<String> thisrow = matrix.get(row.getKey());
			if (thisrow == null) {
				thisrow = row.getValue();
				matrix.put(row.getKey(), thisrow);
			} else {
				for (String s: row.getValue().elementSet())
					thisrow.add(s, row.getValue().count(s));
			}
		}
		for (String s: cm.rowTotals.elementSet())
			rowTotals.add(s, cm.rowTotals.count(s));
		for (String s: cm.colTotals.elementSet())
			colTotals.add(s, cm.colTotals.count(s));
		return this;
	}

}

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

package featurestream.utils;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.mahout.math.Vector;

import java.util.HashMap;
import java.util.Map;

// TODO - replace by Properties?
public class Stats {
	
	@Override
	public String toString() {
		Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
		return "Stats [stats=" + gson.toJson(map) + "]";
	}

	private Map<String,Object> map;
	
	public Stats() {
		map = new HashMap<String,Object>();
	}
	
	public void add(String key, Object value) {
		map.put(key,value);
	}

	// convenience methods for converting certain types
	public void add(String key, Stats value) {
		map.put(key,value.getStats());
	}
	
	public void add(String key, Vector value) {
		map.put(key,DataUtils.toArray(value));
	}

	public Object get(String key) {
		return map.get(key);
	}
	
	public void setStats(Map<String,Object> stats) {
		this.map = stats;
	}
	
	public Map<String,Object> getStats() {
		return map;
	}
	
	public void union(Stats stats) {
		this.map.putAll(stats.map);
	}
	
}

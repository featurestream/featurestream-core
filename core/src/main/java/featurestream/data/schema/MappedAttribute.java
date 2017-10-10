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

package featurestream.data.schema;

import featurestream.data.Event.Entry.Type;

import java.util.HashMap;
import java.util.Map;

public class MappedAttribute extends Attribute {
	private static final long serialVersionUID = 1L;

	@Override
	public String toString() {
		return "MappedAttribute [type=" + type + ", n_values="+nValues()+"]";
	}

	// for categoric attributes
	public Map<String,Double> value_map;
	public Map<Double,String> inv_map;
	
	public int nValues() {
		return value_map.size();
	}

	public String unmapValue(Double v) {
		// FIXME what if v is non-integer?
		return inv_map.get(v);
	}

	public Double mapValue(Number value) {
		return mapValue(value.toString());
	}

	public Double mapValue(String value) {
		if (value_map.containsKey(value))
			return value_map.get(value);
		else {
			double v = nValues();
			value_map.put(value, v);
			inv_map.put(v, value);
			return v;
		}
	}

	public MappedAttribute() {
		super(Type.CATEGORIC);
		this.value_map = new HashMap<String,Double>();
		this.inv_map = new HashMap<Double,String>();
	}

	public Attribute merge(Attribute value) {
		MappedAttribute a = (MappedAttribute) value;
		for (String s:a.value_map.keySet())
			mapValue(s);
		return this;
	}

}

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
import featurestream.utils.FeaturestreamException;

import java.io.Serializable;


public abstract class Attribute implements Serializable{
	private static final long serialVersionUID = 1L;

	protected Type type;

	public boolean isNumeric() {
		return (type == Type.NUMERIC);
	}

	// normalize the value v of an attribute into [-1,1]
	public double normalize(double v) {
		return 0.0;
	}

	public double denormalize(double v) {
		return 0.0;
	}
	
	public Type getType() {
		return type;
	}

	public abstract int nValues();

	public abstract Object unmapValue(Double v);
	
	public Double mapValue(Object o) {
		switch(this.type) {
		case NUMERIC:
			if (o instanceof Number)
				return mapValue((Number)o);
			else {
				try {
					return mapValue(Double.parseDouble(o.toString()));
				} catch (NumberFormatException e) { throw new FeaturestreamException(e); }
			}
		case CATEGORIC:
			return mapValue(o.toString());
		default:
			throw new FeaturestreamException("cannot map value=["+o+"] into attribute="+this.toString());
		}
	}

	public abstract Double mapValue(Number v);
	
	public abstract Double mapValue(String v);
	
	public Attribute(Type type) {
		this.type = type;
	}

	// merge this attribute with another. Returns itself after merging.
	public abstract Attribute merge(Attribute value);

}
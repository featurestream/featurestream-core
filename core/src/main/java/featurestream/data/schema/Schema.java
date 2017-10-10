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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import featurestream.data.Event;
import featurestream.data.Event.Entry;
import featurestream.data.Event.Entry.Type;
import featurestream.data.Instance;
import featurestream.data.transform.DefaultTransformer;
import featurestream.data.transform.EventTransformer;
import featurestream.utils.FeaturestreamException;
import featurestream.utils.HDFSUtils;
import featurestream.utils.Stats;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.Vector.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Schema implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(Schema.class);

	@Override
	public String toString() {
		return "Schema [attrs=" + attrs + "]";
	}

    // event transform pipeline
	EventTransformer transformer;

    // attribute classes
	public Map<String,Attribute> attrs;

	BiMap<String,Integer> attr_order;
	String target;
	
	public Schema(String target, Type type) {
		this.attrs = new HashMap<String,Attribute>();
		this.target = target;
		this.attr_order = HashBiMap.create();
		this.transformer = new DefaultTransformer(this);
        addAttribute(target, type);
    }
	
	public String getTarget() {

        return target;
	}

    public boolean isTarget(String name) {

        return this.target.equals(name);
    }
	
	private void addAttribute(String name, Attribute attr) {
		if (attrs.containsKey(name))
			return;
		
		attrs.put(name, attr);
		attr_order.put(name,attr_order.size());
		log.debug("added new attribute {} {}",name,attr);
	}
	
	public void addAttribute(String name, Type type) {
		switch (type) {
		case CATEGORIC:
			if (isTarget(name))
				addAttribute(name, new MappedAttribute()); // targets need to be recovered
			else
				addAttribute(name, new HashedAttribute(11));
			break;
		case NUMERIC:
			addAttribute(name, new NumericAttribute());
			break;
		case DATETIME:
			addAttribute(name, new DateTimeAttribute());
			break;
		case TEXT:
			addAttribute(name, new TextAttribute());
			break;
		default:
			log.warn("cannot create attribute for unknown Entry.Type={}"+type);
		}
	}

    public int numAttrs() {
        return attrs.size();
    }

	// attribute by name
	public Attribute getAttribute(String name) {
		return attrs.get(name);
	}

    // attribute by index (for dense instances)
    public Attribute getAttribute(int index) {
        return getAttribute(getAttributeName(index));
    }

    public boolean hasAttribute(String name) {
		return getAttribute(name) != null;
	}
	
	public String getAttributeName(int index) {
		return attr_order.inverse().get(index);
	}
	
	public int getAttributeIndex(String name) {
		Integer i = attr_order.get(name);
		return (i==null) ? -1 : i;
	}

	// update schema with event
	public void update(Event event) {

		log.debug("update schema with event={}",event);
		
		// add new attributes
		for (Entry entry: event.getData()) {
			String k = entry.getName();
			Object v = entry.getValue();
			Type t = entry.getType();
			if (v==null)
				continue;

			Attribute a;
			if (!attrs.containsKey(k)) {
				// determine type if t is not set
				if (t == null) {
					if (v instanceof Number)
						t = Type.NUMERIC;
					else if (v instanceof String)
						t = Type.CATEGORIC;
					else if (v instanceof Boolean)
						t = Type.CATEGORIC;
					else {
						log.warn("skipping entry key={}, unrecognized type={} value={}",k,v.getClass().getName(),v);
						continue;
					}
				}
				assert (t!=null);
				addAttribute(k,t);
				a = getAttribute(k);
			}
			else {
				a = attrs.get(k);
				// if event has explicit type, check attribute type is consistent
				if (t != null && !a.getType().equals(t))
						throw new FeaturestreamException("Cannot change type of attribute="+k+", current_type="+a.type+", entry="+entry);
			}
			
			if (t==Type.TEXT || t==Type.DATETIME)
				continue; // ignore - these types are expanded by EventTransformers

			assert (a!=null);
			a.mapValue(v);
		} // next entry 
	}

	// transform an event into a set of dense instances, one per target, using the schema
	public Instance transform(Event event) {
		if (event==null)
			return null;

		// apply any event transforms
		event = transformer.transform(event);
		
        // TODO use sparsevector
        DenseVector vector = new DenseVector(attrs.size());
        vector.assign(Double.NaN);

        double label = -1;
        for (Entry e:event.getData()) {
            String k = e.getName();
            Type t = e.getType();
            Object v = e.getValue();
            if (v==null)
                continue;
            if (t==Type.TEXT || t==Type.DATETIME)
                continue; // ignore - these types are expanded by EventTransformers

            if (hasAttribute(k)) {
                Integer i = attr_order.get(k);
                Attribute a = attrs.get(k);
                double value = a.mapValue(v);

                // if this is the target, don't include in feature vector
                if (k.equals(this.target))
                    label = value;
                else
                    vector.set(i,value);
            }
        }
        if (label==-1) // flag if no label was set
            log.error("no label for target={}, event={}",target,event);

        return new Instance(vector,label);
	}
	
	// merge this schema with another schema. Returns itself after merging.
	public Schema merge(Schema schema) {
		for (Map.Entry<String,Attribute> e:schema.attrs.entrySet())
			if (attrs.containsKey(e.getKey()))
				// merge attributes
				getAttribute(e.getKey()).merge(e.getValue());
			else
				addAttribute(e.getKey(),e.getValue());
		return this;
	}
	
	// unmap a value from a prediction
	// TODO do this in a different class
	// TODO use feature hashing
	public Object unmapAttribute(String attr, Double value) {
		Attribute a = getAttribute(attr);
		return (a==null) ? null : a.unmapValue(value);	
	}

	// FIXME
	// hack for now: strip back until the rightmost '_'
	private String getParentAttribute(String attr) {
		int i = attr.lastIndexOf('_');
		if (i==-1)
			return attr;
		else
			return attr.substring(0, i);	
	}
	
	private boolean isInternalAttribute(String attr) {
        return attr.lastIndexOf('_') != -1;
	}

	// unmap vector attribute indices to strings
	// if group_parent_features = true, then aggregate the importances into top-level features
	// in order to avoid returning importances of internal features
	public Map<String,Double> unmapVectorIndexes(Vector in, boolean group_parent_features) {
		Map<String,Double> out = new HashMap<String,Double>(in.size());
		for (Element e: in.nonZeroes()) {
			if (group_parent_features) {
				String p_attr = getParentAttribute(getAttributeName(e.index()));
				Double d = out.get(p_attr);
				if (d==null)
					d = 0.0;
				out.put(p_attr, e.get()+d);
			}
			else
				out.put(getAttributeName(e.index()), e.get());
		}
		return out;
	}
	
	// unmap vector indices to strings
	// unmap values
	public Map<String,Object> unmapVector(Vector in, boolean ignore_internal_features) {
		Map<String,Object> out = new HashMap<String,Object>(in.size());
		for (Element e: in.all()) {
			String attr = getAttributeName(e.index());
			if (ignore_internal_features && isInternalAttribute(attr))
				continue;
			else
				out.put(attr, unmapAttribute(attr, e.get()));
		}
		return out;
		
	}

	public Map<String,Double> unmapTargetValues(double[] prediction, String target) {
		Map<String,Double> out = new HashMap<String,Double>(prediction.length);
		for (int i=0;i<prediction.length;i++) {
			Object o = unmapAttribute(target,(double)i);
			if (o != null) {
				assert (o instanceof String);
				out.put((String)o,prediction[i]);
			}
		}
		return out;
	}
	
	public Stats getInfo(boolean show_internal_attributes) {
		Stats stats = new Stats();
		stats.add("target", target);
		Stats attr_stats = new Stats();
		for (Map.Entry<String,Attribute> e : attrs.entrySet()) {
			if (show_internal_attributes || !isInternalAttribute(e.getKey()))
				attr_stats.add(e.getKey(), e.getValue().toString());
		}
		stats.add("attributes", attr_stats);
		return stats;
	}

	public Stats getInfo() {
		return getInfo(false);
	}
	
	public static void writeSchema(String checkpointPath, Schema schema) throws IOException {
		HDFSUtils.writeObject(checkpointPath,"schema",schema);
	}
	
	public static Schema readSchema(String checkpointPath) {
		return (Schema) HDFSUtils.readObject(checkpointPath, "schema");
	}

	public int getNumLabels() {
		return getAttribute(target).nValues();
	}

}


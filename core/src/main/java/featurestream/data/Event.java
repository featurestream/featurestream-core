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
package featurestream.data;



import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Event implements Serializable{
	private static final long serialVersionUID = 1L;

	@Override
	public String toString() {
		return "Event [timestamp=" + timestamp + ", data=" + data + "]";
	}

	private long timestamp;
	
	public Event(long timestamp, ArrayList<Entry> data) {
		super();
		this.timestamp = timestamp;
		this.data = data;
	}
	
	public Event(){}

	private ArrayList<Entry> data;

	public static class Entry implements Serializable{
		
		public enum Type {
			NUMERIC,
			CATEGORIC,
			TEXT,
			DATETIME;
		};
		
		private String name;
		private Object value;
		private Type type;

		@Override
		public String toString() {
			return name + ":" + value + ((type==null) ? "" : "("+type+")");
		}

		public Entry(String name, Object value, Type type) {
			this.name = name;
			this.value = value;
			this.type = type;
		}

		public Entry(){}

		public String getName() {
			return name;
		}
	
		public void setName(String name) {
			this.name = name;
		}
	
		public Object getValue() {
			return value;
		}
	
		public void setValue(Object value) {
			this.value = value;
		}

		public Type getType() {
			return type;
		}
		
		public void setType(Type type) {
			this.type = type;
		}
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public List<Entry> getData() {
		return data;
	}

	public void setData(ArrayList<Entry> data) {
		this.data = data;
	}
	
	public void addEntry(Entry entry) {
		if (data == null)
			data = new ArrayList<Entry>();
		data.add(entry);
	}
		
}


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

package featurestream.data.transform;

import featurestream.data.Event;
import featurestream.data.Event.Entry;
import featurestream.data.transform.date.StringToTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

// detect datetime instances, and expand them to features {Y,M,D,H,M,S}
// optionally convert to UTC
public class DateTimeTransformer implements EventTransformer {
	private static final Logger log = LoggerFactory.getLogger(DateTimeTransformer.class);
	
	private DateFormat format;

	public DateTimeTransformer() {
		this.format = null;
		log.info("Created DateTime transformer, no pattern specified, auto pattern recognition");
	}

	// push the pattern into event.entry ?
	public DateTimeTransformer(String pattern) {
		this.format = new SimpleDateFormat(pattern);
		this.format.setLenient(true);
		log.info("Created DateTime transformer, pattern={}",pattern);
	}
	
	public Event transform(Event event) {
		log.debug("transform: in={}",event);

		ArrayList<Entry> new_entries = new ArrayList<Entry>();
		for (Entry entry : event.getData()) {
			String k = entry.getName();
			Object v = entry.getValue();
			Entry.Type t = entry.getType();
			if (v instanceof String || t == Entry.Type.DATETIME) {
				Date d=null;
				if (format != null)
					try {
						d = format.parse((String)v);
					} catch (ParseException e) { log.info("error transforming entry="+entry+", error=["+e.getMessage()+"]"); }
				// FIXME: make stringToTime auto parsing less lenient
 				// currently, it parses strings such as '2 valve' or '98 foo' or '98' as date objects
  				else {
					Object o = StringToTime.date((String) v);
					if (o instanceof Date)
						d = (Date) o;
				}
				if (d != null) {
					entry.setType(Entry.Type.DATETIME);
					// TODO check these types are suitable
					new_entries.add(new Entry(k+"_y", d.getYear(), Entry.Type.CATEGORIC));
					new_entries.add(new Entry(k+"_m", d.getMonth(), Entry.Type.CATEGORIC));
					new_entries.add(new Entry(k+"_d", d.getDay(), Entry.Type.CATEGORIC));
					new_entries.add(new Entry(k+"_dm", d.getDate(), Entry.Type.NUMERIC));
					new_entries.add(new Entry(k+"_h", d.getHours(), Entry.Type.NUMERIC));
					new_entries.add(new Entry(k+"_m", d.getMinutes(), Entry.Type.NUMERIC));
					new_entries.add(new Entry(k+"_s", d.getSeconds(), Entry.Type.NUMERIC));
					new_entries.add(new Entry(k+"_t", d.getTime(), Entry.Type.NUMERIC));
					new_entries.add(new Entry(k+"_tzo", d.getTimezoneOffset(), Entry.Type.NUMERIC));
				}
			}
		}

		for (Entry e : new_entries)
			event.addEntry(e);

		log.debug("transform: out={}",event);
		return event;
	}

}

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
import featurestream.data.schema.Schema;
import featurestream.utils.MurmurHash3;
import org.apache.mahout.math.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Random;

public class StringTokenizer implements EventTransformer {
	private static final Logger log = LoggerFactory.getLogger(StringTokenizer.class);
	
	Schema schema;
	String regex; // http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html#sum
	final int n_features = (1<<4) - 1; // uses 2*n_features, in (-n_features, n_features)
	int probes;
	int[] seeds;
	int n_grams;
	boolean clean;
	
	public StringTokenizer(Schema schema, String regex, int probes, int n_grams) {
		this.schema = schema;
		this.clean = true;// if we should strip all punctuation and transform to lower case
		this.regex = regex;
		this.probes = probes;
		this.n_grams = n_grams;
		Random rng = new Random();
		this.seeds = new int[probes];
		for (int i=0;i<probes;i++)
			seeds[i]=rng.nextInt();
		log.info("created string tokenizer, n_features={}, regex={}, n_probes={}, n_grams={}", n_features, regex, probes, n_grams);
	}
	
	public StringTokenizer(Schema schema) {
		this(schema,"\\s+",2,2);
	}
	
	public Event transform(Event event) {
		log.debug("transform: in={}",event);

		ArrayList<Entry> new_entries = new ArrayList<Entry>();
		for (Entry entry : event.getData()) {
			String k = entry.getName();
			Object v = entry.getValue();
			
			if (schema.isTarget(k))
				continue;
			
//			Entry.Type t = entry.getType();
//			if (v instanceof String && t == Entry.Type.TEXT)
			if (v instanceof String) {
				String s = (String) v;
				if (clean) {
					// strip punctuation
					s = s.replaceAll("[^\\p{L}]", " ");// FIXME appears to catch numbers..
					// lower case
					s = s.toLowerCase();
				}
				String[] words = s.split(regex);
				if (words.length > 1) {
					// trim whitespace remaining after split
					for (int i=0;i<words.length;i++)
						words[i] = words[i].trim();
					log.info("v={}, s={}, words={}",v,s,Arrays.toString(words));
					for (int j=0; j<n_grams; j++) {
						for (int start=0, end=j; end<words.length; start++,end++) {
							StringBuffer word = new StringBuffer(words[start]);
							for (int l=start+1;l<=end;l++)
								word.append(' ').append(words[l]);
							s = word.toString();
							for (int i=0;i<probes;i++) {
								String key = k+"_"+Integer.toString(MurmurHash3.murmurhash3_x86_32(s, 0, s.length(), seeds[i]) % n_features);
								new_entries.add(new Entry(key, s, Entry.Type.CATEGORIC));
							}
						}
					}
					entry.setType(Entry.Type.TEXT);
				}
			}
		}

		for (Entry e : new_entries)
			event.addEntry(e);

		log.debug("transform: out={}",event);
		return event;
	}

}

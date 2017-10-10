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

import com.clearspring.analytics.stream.frequency.CountMinSketch;
import com.google.common.util.concurrent.AtomicLongMap;
import featurestream.data.Event;
import featurestream.data.Event.Entry;
import featurestream.utils.MurmurHash3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.Random;

public class TFIDFVectorizer implements EventTransformer, Externalizable {
	private static final Logger log = LoggerFactory.getLogger(TFIDFVectorizer.class);
	
	int n_features; // uses 2*n_features, in (-n_features, n_features)
	int[] seeds;
	boolean clean;
	CountMinSketch counts;
	int seed;
	double n_events;
	
	String regex; // the regex to use for splitting text // http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html#sum
	int probes; // the number of probes for each n-gram
	int n_grams; // the max number of n_grams to consider
	int min_term_length; // ignore terms with length strictly less than this
	boolean binary; // if true, all weights>0.0 are considered as 1.0
	boolean strip_punctuation; // attempt to strip punctuation
	boolean lowercase; // convert terms to lowercase
	double max_df; // ignore terms that have a df strictly higher than the given threshold (corpus-specific stop words)
	double min_df; // ignore terms that have a df strictly lower than the given threshold (cut-off).
	boolean sublinear_tf; // if true, replace tf by 1+log(tf)

	public TFIDFVectorizer(Properties props) {
		this.n_features = Integer.parseInt(props.getProperty("n_features","100"));
		this.regex = props.getProperty("regex","\\s+");
		this.probes = Integer.parseInt(props.getProperty("probes","2"));
		this.n_grams = Integer.parseInt(props.getProperty("n_grams","2"));
		this.min_term_length = Integer.parseInt(props.getProperty("probes","2"));
		this.binary = Boolean.parseBoolean(props.getProperty("binary","false"));
		this.strip_punctuation = Boolean.parseBoolean(props.getProperty("strip_punctuation","false"));
		this.lowercase = Boolean.parseBoolean(props.getProperty("lowercase","true"));
		this.max_df = Double.parseDouble(props.getProperty("max_df","1.0"));
		this.min_df = Double.parseDouble(props.getProperty("min_df","0.0"));
		this.sublinear_tf = Boolean.parseBoolean(props.getProperty("sublinear_tf","false"));

		clean = true;// if we should strip all punctuation and transform to lower case
		Random rng = new Random();
		seeds = new int[probes];
		for (int i=0;i<probes;i++)
			seeds[i]=rng.nextInt();
		counts = new CountMinSketch(0.01, 0.95, rng.nextInt());
		seed = rng.nextInt();
		n_events = 0;
		
		log.info("created string tokenizer, n_features={}, regex={}, n_probes={}, n_grams={}", n_features, regex, probes, n_grams);
	}
	
	public TFIDFVectorizer() {
		this(new Properties());
	}
	
	public Event transform(Event event) {
		log.debug("transform: in={}",event);

		ArrayList<Entry> new_entries = new ArrayList<Entry>();
		for (Entry entry : event.getData()) {
			String k = entry.getName();
			Object v = entry.getValue();
			
			Entry.Type t = entry.getType();
			if (v instanceof String && t == Entry.Type.TEXT) {					
				String s = (String) v;
				if (strip_punctuation)
					s = s.replaceAll("[^\\p{L}]", " ");// FIXME appears to catch numbers..
				if (lowercase)
					s = s.toLowerCase();					

				// FIXME ignore words of length <= min_term_length
				String[] words = s.split(regex);
				if (words.length > 0) {
					// we're vectorizing it
					n_events++;
					
					// trim whitespace remaining after split
					for (int i=0;i<words.length;i++)
						words[i] = words[i].trim();

					// build set of terms
					AtomicLongMap<Long> tfs = AtomicLongMap.<Long>create();
					HashMap<Long,CharSequence> terms = new HashMap<Long,CharSequence>();
					for (int j=0; j<n_grams; j++) {
						for (int start=0, end=j; end<words.length; start++,end++) {
							StringBuffer term = new StringBuffer(words[start]);
							for (int l=start+1;l<=end;l++)
								term.append(' ').append(words[l]);
							// update tf counts
							long term_id = MurmurHash3.murmurhash3_x86_32(term, 0, term.length(), seed);
							tfs.incrementAndGet(term_id);
							terms.put(term_id, term);
						}
					}

					// update df counts
					for (Long term_id: tfs.asMap().keySet())
						counts.add(term_id, 1);

					// tf[term] = sublinear tf scaling, replace tf with 1 + log(tf).
					// idf[term] = 1+log(n_events/(1+counts[term])) // Smooth idf weights by adding one to document frequencies

					// create entries with tfidf weights
					for (Long term_id: tfs.asMap().keySet()) {
						CharSequence term = terms.get(term_id);
						double tf = tfs.get(term_id);
						if (sublinear_tf)
							tf = 1.0+Math.log(tf);
						double df = counts.estimateCount(term_id) / n_events;
						log.debug("term={} tf={} df={}",term,tf,df);
						if (df < min_df || df > max_df)
							continue; // skip this term
						double idf = 1.0+Math.log(1.0/df); // TODO stable to do 1/df? 
						double weight = tf*idf;
						if (binary)
							weight = (weight > 0.0) ? 1.0 : 0.0;
						log.debug("term={} tf={} df={} idf={} tfidf={}",term,tf,df,idf,weight);
						for (int i=0;i<probes;i++) {
							int ix = (int) ((seeds[i]*term_id) % n_features);
							String key = k+"_"+ix;
							new_entries.add(new Entry(key, weight, Entry.Type.NUMERIC)); // FIXME if binary, use CATEGORIC type?
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

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		byte[] cms = CountMinSketch.serialize(counts);
		out.writeInt(cms.length);
		out.write(cms);
		out.writeInt(n_features);
		out.writeObject(seeds);
		out.writeInt(seed);
		out.writeDouble(n_events);
		out.writeObject(regex);
		out.writeInt(probes);
		out.writeInt(n_grams);
		out.writeInt(min_term_length);
		out.writeBoolean(binary);
		out.writeBoolean(strip_punctuation);
		out.writeBoolean(lowercase);
		out.writeDouble(max_df);
		out.writeDouble(min_df);
		out.writeBoolean(sublinear_tf);
		
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		int len = in.readInt();
		byte[] b = new byte[len];
		in.readFully(b);
		CountMinSketch.deserialize(b);
		n_features = in.readInt();
		seeds = (int[]) in.readObject();
		seed = in.readInt();
		n_events = in.readDouble();
		regex = (String) in.readObject();
		probes = in.readInt();
		n_grams = in.readInt();
		min_term_length = in.readInt();
		binary = in.readBoolean();
		strip_punctuation = in.readBoolean();
		lowercase = in.readBoolean();
		max_df = in.readDouble();
		min_df = in.readDouble();
		sublinear_tf = in.readBoolean();
	}

}

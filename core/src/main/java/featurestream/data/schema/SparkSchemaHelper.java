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

import featurestream.data.Event;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function2;

import java.util.Arrays;
import java.util.Iterator;

public class SparkSchemaHelper {

	public static Schema updateSchema(final Schema schema, JavaRDD<Event> events) {
		if (events.count() == 0)
			return schema;

		// TODO pass a schema template instead of actual schema
		// need to hash attribute values (eg in mapped attribute) for this to be possible
		
		Schema schemaDelta = events.mapPartitions(new FlatMapFunction<Iterator<Event>,Schema>() {
			public Iterable<Schema> call(Iterator<Event> arg0) throws Exception {
				while (arg0.hasNext())
					schema.update(arg0.next());
				return Arrays.asList(schema);
			}			
		}).reduce(new Function2<Schema,Schema,Schema>() {
			// merge schemas
			public Schema call(Schema s1, Schema s2) throws Exception {
				return s1.merge(s2);			
			}
		});
		return schema.merge(schemaDelta); // do this final merge so that the arg passed is modified
	}

}

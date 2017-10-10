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
import featurestream.data.schema.Schema;

public class DefaultTransformer implements EventTransformer {
	
	protected EventTransformer[] transformers;

	public DefaultTransformer(Schema schema) {
		transformers = new EventTransformer[]{ }; //new TFIDFVectorizer() };//new DateTimeTransformer(), new TFIDFVectorizer() };
	}

	public Event transform(Event event) {
		for (EventTransformer transformer : transformers)
			event = transformer.transform(event);
		return event;
	}

}

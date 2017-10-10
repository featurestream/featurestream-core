package featurestream.data.schema;

import featurestream.data.Event;

public class TextAttribute extends Attribute {
	
	public TextAttribute() {
		super(Event.Entry.Type.TEXT);
	}

	@Override
	public int nValues() {
		return 0;
	}

	@Override
	public Object unmapValue(Double v) {
		return null;
	}

	@Override
	public Double mapValue(Number v) {
		return null;
	}

	@Override
	public Double mapValue(String v) {
		return 0.0;
	}

	@Override
	public Attribute merge(Attribute value) {
		return this;
	}

}

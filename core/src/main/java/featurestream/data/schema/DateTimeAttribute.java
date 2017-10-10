package featurestream.data.schema;

import featurestream.data.Event;

public class DateTimeAttribute extends Attribute {
	
	public DateTimeAttribute() {
		super(Event.Entry.Type.DATETIME);
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

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

package featurestream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import featurestream.classifier.Learner;
import featurestream.data.schema.Schema;
import featurestream.classifier.LearnerFactory.LearnerType;
import featurestream.utils.HDFSUtils;

import java.io.IOException;
import java.io.Serializable;

@JsonIgnoreProperties({"schema","local","learner"})
public class Stream implements Serializable {
	
	public enum Status { 
		WAITING,
		PROCESSING,
		CLOSED,
		ERROR
	}

	@Override
	public String toString() {
		return "Stream [streamId=" + streamId + ","
				+ ", learnerType=" + learnerType + ", lastChecked="
				+ lastChecked + ", checkInterval=" + checkInterval
				+ ", driver="+driver+", basePath=" + basePath + "]";
	}

	Long streamId;
	LearnerType learnerType;

	public long lastChecked;
	long checkInterval;
	String basePath;
	Status status;
	String driver;
    String target;
	public transient Schema schema;
	public transient Learner learner;
	
	public Stream() {}
	
	public Stream(Long streamId, String target, LearnerType learnerType, Schema schema, long checkInterval, String basePath) {
		this.streamId = streamId;
		this.learnerType = learnerType;
		this.schema = schema;
		this.target = target;
		this.lastChecked = Long.MIN_VALUE;
		this.checkInterval = checkInterval;
		this.basePath = basePath;
		this.status = Status.WAITING;
		this.driver = null;
		this.learner = null;
	}
	
	public long getCheckInterval() {
		return checkInterval;
	}
	
	public void setCheckInterval(long checkInterval) {
		this.checkInterval = checkInterval;
	}
	
	public String getDriver() {
		return driver;
	}
	
	public void setDriver(String driver) {
		this.driver = driver;
	}
	
	public Status getStatus() {
		return status;
	}
	
	public void setStatus(Status status) {
		this.status = status;
	}
	
	public String getBasePath() { 
		return basePath;
	}
	
	public String getTarget() {
		return target;
	}
	
	public void setTarget(String targets) {
		this.target = target;
	}
	
	public long getStreamId() { 
		return streamId;
	}

	public LearnerType getLearnerType() {
		return learnerType;
	}

	public Schema getSchema() {
		return schema;
	}
	
	public Learner getLearner() {
		return learner;
	}
	
	public boolean shouldCheck() {

		if (checkInterval+lastChecked < System.currentTimeMillis()) {
			System.out.println("checking stream: lastChecked="+lastChecked+", now="+System.currentTimeMillis()+" checkInterval="+checkInterval);
			return true;
		}
        else
    		return false;
	}

	public static Stream readStream(String basePath, Long streamId) {
		// load the stream from HDFS, if it exists
		Object o = HDFSUtils.readObject(basePath+"/"+streamId+"/", "stream", true, Stream.class);
		if (o==null)
			return null;
		else {
			Stream s = (Stream) o;
			s.lastChecked = Long.MIN_VALUE; //reset last checked time			
			// read schema
//			s.schema = Schema.readSchema(s.getBasePath());
			// read learner
			s.learner = (Learner) Learner.read(s.getBasePath());
			s.schema = s.learner.getSchema();
			return s;
		}
	}

	public static void writeStream(String basePath, Stream stream) throws IOException {
		// write as JSON
		HDFSUtils.writeObject(basePath,"stream",stream,true);
	}

}
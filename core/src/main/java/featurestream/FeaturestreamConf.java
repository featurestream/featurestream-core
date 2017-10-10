/* 
 * Copyright (C) 2013 Andrew Twigg - All Rights Reserved
 * Unauthorized copying or distribution 
 * of this file, via any medium, is strictly prohibited
 * Proprietary and confidential.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package featurestream;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown=true)
public class FeaturestreamConf {


	@Override
	public String toString() {
		return "FeaturestreamConf [sparkMaster=" + sparkMaster
				+ ", dataPath=" + dataPath + ", sparkHome=" + sparkHome
				+ ", jar=" + jar + ", hdfsURL=" + hdfsURL;
	}

	@JsonProperty
    private String sparkMaster;
	public String getSparkMaster() {
		return sparkMaster;
	}

    @JsonProperty
    private String dataPath;
	public String getDataPath() {
		return dataPath;
	}

    @JsonProperty
    private String sparkHome;
	public String getSparkHome() {
		return sparkHome;
	}

    @JsonProperty
    private String jar;
	public String getJar() {
		return jar;
	}

    @JsonProperty
    private String hdfsURL;
	public String getHdfsURL() {
		return hdfsURL;
	}
	
    @JsonProperty
    private Map<String,String> api;   
    public Map getApi() {
    	return api;
    }
    
        
	
}
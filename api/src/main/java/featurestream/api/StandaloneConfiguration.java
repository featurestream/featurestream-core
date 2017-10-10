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

package featurestream.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yammer.dropwizard.config.Configuration;

@JsonIgnoreProperties(ignoreUnknown=true)
public class StandaloneConfiguration extends Configuration {

    @JsonProperty
    private String configPath;
    public String getConfigPath() {
    	return configPath;
	}
	
}
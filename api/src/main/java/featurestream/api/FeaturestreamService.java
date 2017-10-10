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

package featurestream.api;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import featurestream.api.resources.StreamResources;
import org.slf4j.LoggerFactory;

public class FeaturestreamService extends Service<StandaloneConfiguration> {
	
	public static void main(String[] args) throws Exception {

		// assume SLF4J is bound to logback in the current environment
	    LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
	    // print logback's internal status
	    StatusPrinter.print(lc);
	    new FeaturestreamService().run(args);
    }

    @Override
    public void initialize(Bootstrap<StandaloneConfiguration> bootstrap) {
        bootstrap.setName("featurestream");
    }

    @Override
    public void run(StandaloneConfiguration conf,
                    Environment environment) {
    	
    	String configPath = conf.getConfigPath();
    	environment.addResource(new StreamResources(configPath));
    	environment.addHealthCheck(new FeaturestreamHealthCheck());
    }

}
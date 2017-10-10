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

import com.yammer.metrics.core.HealthCheck;

public class FeaturestreamHealthCheck extends HealthCheck {

	public FeaturestreamHealthCheck() {
		super("featurestream");
	}

	@Override
    protected Result check() throws Exception {
        return Result.healthy();
    }
}

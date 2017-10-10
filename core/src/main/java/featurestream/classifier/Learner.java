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
package featurestream.classifier;

import featurestream.data.Instance;
import featurestream.data.schema.Schema;
import featurestream.utils.HDFSUtils;
import featurestream.utils.Stats;
import org.apache.mahout.math.Vector;

import java.io.IOException;
import java.io.Serializable;

public abstract class Learner implements Serializable {

	// update the model with a single instance
	public abstract void update(Instance v);

    // batch update method
    public void update(Iterable<Instance> instances) {
        for (Instance v: instances)
            update(v);
    }

	public abstract double predict(Instance instance);
	
	public abstract double[] predictFull(Instance instance);
	
	public abstract Vector featureImportances();	

	public abstract Vector featureImportances(Instance instance);	
	
	// returns information about the learner
	// typically slower to compute
	public abstract Stats getInfo();
	
	public abstract boolean isRegression();
	
	public abstract Stats getStats();
	
	public abstract void clearStats();
	
	public abstract Schema getSchema();

	public static void write(String checkpointPath, Learner model) throws IOException {
		HDFSUtils.writeObject(checkpointPath, "model", model);
	}
		
	public static Learner read(String checkpointPath) {
		return (Learner) HDFSUtils.readObject(checkpointPath, "model");
	}
	
	public abstract Vector transform(Instance instance);

}
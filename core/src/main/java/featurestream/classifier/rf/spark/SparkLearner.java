package featurestream.classifier.rf.spark;

import featurestream.classifier.Learner;
import featurestream.data.Instance;
import org.apache.spark.api.java.JavaRDD;

public abstract class SparkLearner extends Learner {
	
	// update the current model with the rdd
	public abstract void update(JavaRDD<Instance> data);
	
	// update the model with a dstream
//	public abstract void update(JavaDStream<Instance> data);

	
}
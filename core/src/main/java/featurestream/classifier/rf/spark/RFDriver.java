package featurestream.classifier.rf.spark;

import featurestream.classifier.LearnerFactory;
import featurestream.classifier.summary.ResultSummarizer;
import featurestream.data.Event;
import featurestream.data.Instance;
import featurestream.data.schema.Schema;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;

import java.util.Arrays;
import java.util.Properties;


public class RFDriver {

    private static final Logger log = LoggerFactory.getLogger(RFDriver.class);

    public static void main(String[] args) {

//        LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)

        if (args.length < 6)
            System.out.println("USAGE: RFDriver filename target targetType learnerType master has_header");

        String filename = args[0];
        String target = args[1];
        String targetType = args[2];
        String learnerType = args[3];
        String master = args[4];
        String has_header = args[5];
        final String SEP = ",";

        log.info("filename={}, target={}, targetType={}, learnerType={}, master={}, has_header=[{}]",
                filename, target, targetType, learnerType, master, has_header);

        Event.Entry.Type tType = Event.Entry.Type.valueOf(targetType);
        LearnerFactory.LearnerType lType = LearnerFactory.LearnerType.valueOf(learnerType);

//		System.setProperty("spark.serializer", "spark.KryoSerializer");
//		System.setProperty("spark.kryo.registrator", "featurestream.classifier.tree.spark.MyRegistrator");

        SparkConf conf = new SparkConf().setAppName("RF").setMaster(master);
        JavaSparkContext sc = new JavaSparkContext(conf);

        JavaRDD<String> data = sc.textFile(filename);

        log.info("parsing CSV into Events");

        String[] header_ = data.first().split(SEP);
        if (has_header.equalsIgnoreCase("0")) {
            log.info("no header, replacing");
            // replace with numeric header
            for (int i = 0; i < header_.length; i++)
                header_[i] = Integer.toString(i);
        }
        // now make it final so spark can ship around
        final String[] header = header_;

        log.info("header:\n{}", Arrays.toString(header));

        JavaRDD<Event> events = data.map(new Function<String,Event>() {
            public Event call(String line) throws Exception {
                String[] row = line.split(SEP);
                Event e = new Event();
                for (int i=0;i<row.length;i++) {
                    // try to parse as float
                    Object v;
                    Event.Entry.Type t;
                    try {
                        v = Float.parseFloat(row[i]);
                        t = Event.Entry.Type.NUMERIC;
                    } catch (NumberFormatException ex) {
                        v = row[i];
                        t = Event.Entry.Type.CATEGORIC;
                    }
                    e.addEntry(new Event.Entry(header[i], v, t));
                }
                return e;
            }
        });

        log.info("got {} events", events.count());

        log.info("building schema from RDD");
        final Schema schema = new Schema(target, tType);
        log.info("schema = {}", schema);

        // run events through schema locally to establish schema
        for (Event e : events.take(1000))
            schema.update(e);

        log.info("transforming events");
        JavaRDD<Instance> instances = events.map(new Function<Event,Instance>() {
            public Instance call(Event e) {
                return schema.transform(e);
            }
        }).cache();

        // look at target distribution
        JavaPairRDD labelCounts = instances.mapToPair(new PairFunction<Instance, Double, Integer>() {
            public Tuple2<Double, Integer> call(Instance i) {
                return new Tuple2<Double, Integer>(i.getLabel(), 1);
            }
        }).reduceByKey(new Function2<Integer, Integer, Integer>() {
            public Integer call(Integer integer, Integer integer2) throws Exception {
                return integer+integer2;
            }
        });

        log.info("Label counts:"+labelCounts.collect());

        log.info("building RF model");
        Properties props = new Properties();
        props.setProperty("DELTA", "1.0");
        props.setProperty("SPLIT_FREQ", Integer.toString(Integer.MAX_VALUE));
        props.setProperty("MAX_DEPTH", "10");
        props.setProperty("N_LEARNERS", "10");
        RFModel model = new RFModel(schema, target, lType, props);
        RF.update(model, instances);

        log.info("done");

        // simple accuracy test

        ResultSummarizer summary = RF.updateSummarizer(model, instances);

        log.info(summary.getSummary().toString());



    }
}

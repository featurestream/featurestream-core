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
package featurestream.api.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import featurestream.FeaturestreamConf;
import featurestream.Stream;
import featurestream.classifier.Learner;
import featurestream.classifier.LearnerFactory;
import featurestream.classifier.LearnerFactory.LearnerType;
import featurestream.data.Event;
import featurestream.data.Event.Entry.Type;
import featurestream.data.Instance;
import featurestream.data.schema.Schema;
import featurestream.utils.FeaturestreamException;
import featurestream.utils.HDFSUtils;
import featurestream.utils.Stats;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.servlet.ServletConfig;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.MalformedURLException;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
@Path("/api")
public class StreamResources {

	private static final Logger log = LoggerFactory.getLogger(StreamResources.class);
	private static final Map<Long,ArrayList<String>> buffers = new HashMap<Long,ArrayList<String>>();
	private static final Map<Long, Stream> streams = new HashMap<Long,Stream>();
	private static FeaturestreamConf conf;
	private static Configuration hadoopConf;
	private static int checkpointCount = 5000;
	@Context private ServletConfig sc;
	
	public StreamResources() { // no-args constructor for war deployment
	}
	
	public StreamResources(String configFile) {
		File f = new File(configFile);
		try {
			log.info("resource for "+configFile+" is "+f.getCanonicalPath());
			loadConfig(new FileInputStream(f));
		} catch (IOException e) { log.error("error creating StreamResources",e); }
	}

	@PostConstruct
	public void init() {
		if (conf == null) {
			final String configFile = sc.getInitParameter("fsconfig");
			if (configFile == null)
				throw new RuntimeException("Cannot find fsconfig param in web.xml");
			try {
				log.info("resource for "+configFile+" is "+sc.getServletContext().getResource(configFile));
			} catch (MalformedURLException e) { log.error("error creating StreamResources",e); }
			loadConfig(sc.getServletContext().getResourceAsStream(configFile));
		}
	}

	private void loadConfig(InputStream is) {
		final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		try {
			conf = mapper.readValue(is, FeaturestreamConf.class);
			log.info("loaded config={}", conf);
			is.close();
		} catch (IOException e) {
			log.error("error reading config file",e);
			throw new FeaturestreamException("Internal error reading config file");
		}
		hadoopConf = new Configuration();
		hadoopConf.set("fs.default.name", conf.getHdfsURL());
        // set if you want to use AWS S3
//		hadoopConf.set("fs.s3n.awsAccessKeyId", ACCESS_KEY);
//		hadoopConf.set("fs.s3n.awsSecretAccessKey", SECRET_KEY);
		HDFSUtils.conf = hadoopConf;
	}


	// methods for handling cached streams

	Stream getStream_(Long streamId) throws FeaturestreamException {
		Stream stream = streams.get(streamId);
		if (stream==null) {
			log.info("stream="+streamId+" not known, trying to load");
			stream = Stream.readStream(conf.getDataPath(),streamId);
			if (stream==null)
				throw new FeaturestreamException("unknown stream="+streamId);
			log.info("read stream="+stream.toString());
			streams.put(streamId,stream);
			buffers.put(streamId, new ArrayList<String>(checkpointCount));
		}
		try {
			if (stream.shouldCheck()) {
				stream.learner = (Learner) Learner.read(stream.getBasePath());
				stream.schema = stream.learner.getSchema();
				stream.lastChecked = System.currentTimeMillis();
			}
		} catch(Exception e) { throw new FeaturestreamException(e); }
		return stream;
	}

	// methods for handling stream buffers
	boolean addToBuffer(Stream stream, ArrayList<String> buffer, String json) {
		buffer.add(json);
		boolean checkpointed=false;
		if (buffer.size() >= checkpointCount) {
			try {
				// no event logging in this version
				Random rng = new Random();
				org.apache.hadoop.fs.Path path = new org.apache.hadoop.fs.Path(stream.getBasePath(), "events/"+rng.nextInt(Integer.MAX_VALUE));
				log.info("begin: flushing "+buffer.size()+" events to "+path);
				FSDataOutputStream out = FileSystem.get(hadoopConf).create(path);
				GZIPOutputStream gzout = new GZIPOutputStream(out);
				for (String s:buffer) {
					gzout.write(s.getBytes("UTF-8"));
//					gzout.writeByte('\n');
				}
				gzout.close();
				log.info("done: flushing events to "+path);
				buffer.clear();

				checkpoint(stream);
				checkpointed=true;
			} catch(IOException e) { log.error("error flushing events for stream_id="+stream.getStreamId()+", err="+e); }
		}
		return checkpointed;
	}
	
	void checkpoint(Stream stream) {		
        try {
            Learner.write(stream.getBasePath(), stream.getLearner());
            Schema.writeSchema(stream.getBasePath(), stream.getSchema());
        }
        catch(IOException ioe) { log.error("error checkpointing for stream="+stream.getStreamId(), ioe); }
	}

	// REST API methods
	
	@Path("/health")
	@GET
	public Response health() {
		if (conf != null)
			return Response.ok().build();
		else
			return Response.serverError().build();
	}

	@Path("/get_streams")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public Response getStreams(@QueryParam("access") String access) {
		try{
			return Response.ok(streams).build();
		}
		catch (FeaturestreamException e) {
			log.error("error in getStreams: access="+access,e);
			return Response.serverError().entity(e.getMessage()).build();
		}
	}

	@Path("/start_stream")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@POST
	public Response startStream(@QueryParam("access") String access, String target, String type) {
		try{
            if (target == null)
                throw new FeaturestreamException("target name missing");
            if (type == null)
                throw new FeaturestreamException("target type missing");
            Type t_type = Type.valueOf(type);
            if (t_type == null)
                throw new FeaturestreamException("unknown target type:"+type+", allowable targets="+Type.values());

            Properties props = new Properties();
            Schema schema = new Schema(target, t_type);
            Long streamId = Math.abs(new Random().nextLong());

			LearnerType learnerType = LearnerFactory.getLearnerTypeFromTargetType(t_type);
            Learner learner = LearnerFactory.getLearner(schema, target, learnerType, props);

			String basePath = conf.getDataPath()+"/"+streamId+"/";
			Stream stream = new Stream(streamId, target, learnerType, schema, 10000, basePath);
			stream.learner=learner;

			// "register" stream
			try { 
				Schema.writeSchema(basePath, schema);
				Stream.writeStream(basePath, stream);
				if (stream.learner!=null)
					Learner.write(basePath, stream.learner);
			} catch (IOException ioe) { throw new FeaturestreamException(ioe); }

			streams.put(streamId, stream);
			buffers.put(streamId, new ArrayList<String>(checkpointCount));

			return Response.ok(stream).build();
		}
		catch (FeaturestreamException e) {
			log.error("error in start_stream: access="+access,e);
			return Response.serverError().entity(e.getMessage()).build();
		}
	}
	
	@Path("/{streamId}/stop_stream")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public Response stopStream(@PathParam("streamId") Long streamId) {
		try {
			Stream stream = getStream_(streamId);
			// update stream status
			stream.setStatus(Stream.Status.CLOSED);

            try {
                Stream.writeStream(stream.getBasePath(), stream);
            } catch(IOException ioe) { throw new FeaturestreamException("couldn't close stream, error="+ioe); }

			streams.remove(streamId);
			buffers.remove(streamId);
			return Response.ok().build();
		}
		catch (FeaturestreamException e) {
			log.error("error: stream_id="+streamId,e);
			return Response.serverError().entity(e.getMessage()).build();
		}
	}
	
	@Path("/{streamId}/checkpoint")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public Response checkpoint(@PathParam("streamId") Long streamId) {
		try {
			Stream stream = getStream_(streamId);
			if (stream != null)
				checkpoint(stream);
			return Response.ok().build();
		}
		catch (FeaturestreamException e) {
			log.error("error: stream_id="+streamId,e);
			return Response.serverError().entity(e.getMessage()).build();
		}
	}

	@Path("/{streamId}/get_stream")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public Response getStream(@PathParam("streamId") Long streamId) {
		try {
			// TODO check access
			Stream stream = getStream_(streamId);
			return Response.ok(stream).build();//ok((new Gson()).toJson(stream));
		}
		catch (FeaturestreamException e) {
			log.error("error: stream_id="+streamId,e);
			return Response.serverError().entity(e.getMessage()).build();
		}
	}

	@Path("/{streamId}/get_stats")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public Response getStats(@PathParam("streamId") Long streamId) {
		try{
			Stream stream = getStream_(streamId);
			if (stream.learner == null)
				throw new FeaturestreamException("Model not built yet");
			Stats stats = stream.learner.getStats();
			return Response.ok(stats.getStats()).build();//ok((new Gson()).toJson(stream.model.summarizer.getSummary()));
		}
		catch (FeaturestreamException e) {
			log.error("error: stream_id="+streamId,e);
			return Response.serverError().entity(e.getMessage()).build();
		}			
	}

	@Path("/{streamId}/clear_stats")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public Response clearStats(@PathParam("streamId") Long streamId) {
		try{
			Stream stream = getStream_(streamId);
			if (stream.learner == null)
				throw new FeaturestreamException("Model not built yet");
			stream.learner.clearStats();
			return Response.ok().build();
		}
		catch (FeaturestreamException e) {
			log.error("error: stream_id="+streamId,e);
			return Response.serverError().entity(e.getMessage()).build();
		}			
	}
	
	@Path("/{streamId}/get_info")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
    public Response getInfo(@PathParam("streamId") Long streamId) {
		try{
			Stream stream = getStream_(streamId);
			if (stream.learner == null)
				throw new FeaturestreamException("Model not built yet");
	    	Stats stats = stream.learner.getInfo();
	    	stats.add("schema",stream.getSchema().getInfo());
	    	stats.add("schema_full",stream.getSchema().getInfo(true));
	    	return Response.ok(stats.getStats()).build();
		}
		catch (FeaturestreamException e) {
			log.error("error: stream_id="+streamId,e);
			return Response.serverError().entity(e.getMessage()).build();
		}			
    }

	@Path("/{streamId}/get_schema")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public Response getSchema(@PathParam("streamId") Long streamId) {
		try{
			Stream stream = getStream_(streamId);
			return Response.ok(stream.schema.getInfo().getStats()).build();//ok((new Gson()).toJson(stream.model.summarizer.getSummary()));
		}
		catch (FeaturestreamException e) {
			log.error("error: stream_id="+streamId,e);
			return Response.serverError().entity(e.getMessage()).build();
		}			
	}

	@Path("/{streamId}/related_fields")
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	public Response relatedFields(@PathParam("streamId") Long streamId) {
		try{
			Stream stream = getStream_(streamId);
			if (stream.learner == null)
				throw new FeaturestreamException("Model not built yet");
			Schema schema = stream.getSchema();
			Learner l = stream.getLearner();
			return Response.ok(schema.unmapVectorIndexes(l.featureImportances(),true)).build();
		}
		catch (FeaturestreamException e) {
			log.error("error: stream_id="+streamId,e);
			return Response.serverError().entity(e.getMessage()).build();
		}
	}

	@Path("/{streamId}/related_fields_instance")
	@Produces(MediaType.APPLICATION_JSON)
	@POST
	public Response relatedFieldsInstance(@PathParam("streamId") Long streamId, final Event event) {
		try{
			Stream stream = getStream_(streamId);
			if (stream.learner == null)
				throw new FeaturestreamException("Model not built yet");
			Schema schema = stream.getSchema();
			Learner l = stream.getLearner();
			Instance instance = schema.transform(event);
            Map resp = schema.unmapVectorIndexes(l.featureImportances(instance),true);
			return Response.ok(resp).build();
		}
		catch (FeaturestreamException e) {
			log.error("error: stream_id="+streamId,e);
			return Response.serverError().entity(e.getMessage()).build();
		}
	}

	@Path("/{streamId}/predict")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@POST
	public Response predict(@PathParam("streamId") Long streamId,
                            @QueryParam("predict_full") boolean predictFull,
                            final Event event) {
		try{
			Stream stream = getStream_(streamId);
			if (stream.learner == null)
				throw new FeaturestreamException("Model not built yet");
			Schema schema = stream.getSchema();
			Learner learner = stream.getLearner();
			Instance instance = schema.transform(event); //
			Stats result = new Stats();

            Learner l = stream.getLearner();
            String target = stream.getTarget();

            if (predictFull && !l.isRegression()) {
                double[] p = l.predictFull(instance);
                result.add(target, schema.unmapTargetValues(p,target));
            }
            else {
                double p = l.predict(instance);
                result.add(target, schema.unmapAttribute(target,p));
            }

			String jsonResponse = (new Gson()).toJson(result.getStats());
			return Response.ok(jsonResponse).build();
		}
		catch (FeaturestreamException e) {
			log.error("error: stream_id="+streamId,e);
			return Response.serverError().entity(e.getMessage()).build();
		}
	}

	@Path("/{streamId}/train")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@POST
	public Response train(@PathParam("streamId") Long streamId, final Event event) {
		try{
			Stream stream = getStream_(streamId); // ensure buffer exists
			ArrayList<String> buffer = buffers.get(streamId);
			Gson gson = new Gson();
			addToBuffer(stream, buffer, gson.toJson(event));	

			// update learner if running locally
//			if (stream.isLocal()) {
//				Instance instance = stream.getSchema().transform(event);
//				stream.getLearner().update(instance);
//			}

			return Response.ok().build();
		}
		catch (FeaturestreamException e) {
			log.error("error: stream_id="+streamId,e);
			return Response.serverError().entity(e.getMessage()).build();
		}
	}

	@Path("/{streamId}/train_batch")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@POST
	public Response trainBatch(@PathParam("streamId") Long streamId, final byte[] data) {
		try{
			// decompress
			Inflater inflater = new Inflater();
			inflater.setInput(data);

			ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
		    byte[] buf = new byte[1024];
		    while (!inflater.finished()) {
		        int count = inflater.inflate(buf);
		        baos.write(buf, 0, count);
		    }
		    baos.close();
		    byte[] output = baos.toByteArray();
			inflater.end();
			String json = new String(output);

			// try to parse
            Gson gson = new Gson();
			ArrayList<Event> events = gson.fromJson(json, new TypeToken<ArrayList<Event>>(){}.getType());
			log.info("train_batch got "+events.size()+" events");

			// train
			Stream stream = getStream_(streamId); // ensure buffer exists
			ArrayList<String> buffer = buffers.get(streamId);
			for (Event event : events)
				addToBuffer(stream, buffer, gson.toJson(event));

//				// update learner if running locally
//				if (stream.isLocal()) {
//					Map<String,Instance> instances = stream.getSchema().transform(event);
//					stream.getLearner().update(instances);
//				}

			return Response.ok().build();
		}
		catch (IOException e) { throw new FeaturestreamException(e); }
		catch (DataFormatException e) { throw new FeaturestreamException(e); }
		catch (FeaturestreamException e) {
			log.error("error: stream_id="+streamId,e);
			return Response.serverError().entity(e.getMessage()).build();
		}
	}


}
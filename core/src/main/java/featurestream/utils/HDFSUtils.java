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
package featurestream.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class HDFSUtils {
	
	static Logger log = LoggerFactory.getLogger(HDFSUtils.class);
	public static Configuration conf;

	public static void writeObject(String checkpointPath, String name, Object obj) throws IOException {
		writeObject(checkpointPath, name, obj, false);
	}

	public static void writeObject(String checkpointPath, String name, Object obj, boolean asJSON) throws IOException {
		org.apache.hadoop.fs.Path path = new org.apache.hadoop.fs.Path(checkpointPath, name);
		log.info("begin: writing {} to {}",name,path);
		try {
			FileSystem fs = FileSystem.get(conf);
			if (fs.exists(path))
				log.info("path already exists, overwriting");
			FSDataOutputStream out = fs.create(path,true);
			if (asJSON) {
				String json = new ObjectMapper().writeValueAsString(obj);
				out.writeUTF(json);
			}
			else {
				ObjectOutputStream os = new ObjectOutputStream(out);
				os.writeObject(obj);
				os.flush();
				os.close();
			}		
			out.close();
			log.info("done: writing {}",name);
		}
		catch (IOException ioe) { throw ioe; }
		catch (Exception e) { log.error("error writing to path " + path,e); }
	}
	
	public static Object readObject(String checkpointPath, String name) {
		return readObject(checkpointPath, name, false, null);
	}

	public static Object readObject(String checkpointPath, String name, boolean asJSON, Class clazz) {
		org.apache.hadoop.fs.Path path = new org.apache.hadoop.fs.Path(checkpointPath, name);
		log.info("begin: reading {} from {}",name,path);
		Object obj = null;
		try {
			FileSystem fs = FileSystem.get(conf);
			if (!fs.exists(path)) {
				log.info("path not found={}", path);
				obj = null;
			}
			else {
				FSDataInputStream in = fs.open(path);
				if (asJSON) {
					String json = in.readUTF();
					obj = new ObjectMapper().readValue(json, clazz);
				}
				else {
					ObjectInputStream os = new ObjectInputStream(in);
					obj = os.readObject();
					os.close();
				}
				assert (obj != null);
				in.close();
				log.info("done: reading {}",name);
			}
		}
		catch (Exception e) { log.error("error reading from path " + path,e); }
		return obj;
	}
}

package featurestream.utils;

import com.google.common.base.Function;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class PathMonitor {
	static Logger log = LoggerFactory.getLogger(PathMonitor.class);

	final Map<Long,String[]> files;
	final Set<String> lastModTimeFiles;
	long lastModTime;
    Path path;
    FileSystem fs;
    
    public PathMonitor(FileSystem fs, Path path) {
    	this.fs = fs;
    	this.path = path;
    	this.lastModTime = 0L;
    	this.files = new HashMap<Long,String[]>();
    	this.lastModTimeFiles = new HashSet<String>();
    	log.info("Started PathMonitor on path={}",path);
    }
    
    public String[] getNewFiles(final long validTime) throws IOException {

    	final Function<Path,Boolean> filter = new Function<Path,Boolean>() {
    		public Boolean apply(Path path) {
    			return !path.getName().startsWith(".");
    		}
    	};

    	class MyFilter implements PathFilter {
    		// Latest file mod time seen in this round of fetching files and its corresponding files
    		long latestModTime = 0L;
    		long lastModTime = 0L;
    		Set<String> latestModTimeFiles = new HashSet<String>();
    		public MyFilter(long lastModTime){
    			this.lastModTime = lastModTime;
    		}
    		public boolean accept(Path path) {
    			try { 

    				if (!filter.apply(path)) {  // Reject file if it does not satisfy filter
    					log.debug("Rejected by filter " + path);
    					return false;
    				} else {              // Accept file only if
    					long modTime = fs.getFileStatus(path).getModificationTime();
    					log.debug("Mod time for " + path + " is " + modTime);
    					if (modTime < lastModTime) {
    						log.debug("Mod time less than last mod time");
    						return false;  // If the file was created before the last time it was called
    					} else if (modTime == lastModTime && lastModTimeFiles.contains(path.toString())) {
    						log.debug("Mod time equal to last mod time, but file considered already");
    						return false;  // If the file was created exactly as lastModTime but not reported yet
    					} else if (modTime > validTime) {
    						log.debug("Mod time more than valid time");
    						return false;  // If the file was created after the time this function call requires
    					}
    					if (modTime > latestModTime) {
    						latestModTime = modTime;
    						latestModTimeFiles.clear();
    						log.debug("Latest mod time updated to " + latestModTime);
    					}
    					latestModTimeFiles.add(path.toString());
    					log.debug("Accepted " + path);
    					return true;
    				}
    			} catch(Exception e) { return false;}
    		}
    	};
    	MyFilter newFilter = new MyFilter(lastModTime); // ugh

    	log.debug("Finding new files at time " + validTime + " for last mod time = " + lastModTime);    
    	FileStatus[] fsArr = fs.listStatus(path, newFilter);
    	String[] newFiles = new String[fsArr.length];
    	for (int i=0;i<fsArr.length;i++)
    		newFiles[i] = fsArr[i].getPath().toString();
    	log.info("New files at time " + validTime + ":\n" + Arrays.toString(newFiles));
    	if (newFiles.length > 0) {
    		// Update the modification time and the files processed for that modification time
    		if (lastModTime != newFilter.latestModTime) {
    			lastModTime = newFilter.latestModTime;
    			lastModTimeFiles.clear();
    		}
    		lastModTimeFiles.addAll(newFilter.latestModTimeFiles);
    		log.debug("Last mod time updated to " + lastModTime);
    	}
    	files.put(validTime, newFiles);
    	return newFiles;
    }

}
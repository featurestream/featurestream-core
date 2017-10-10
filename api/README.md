# Running the API in standalone mode

Unzip the files into a folder, eg `featurestream-core`

Modify `featurestream.yml` by setting `hdfsURL` to point to an hdfs root
(use `file:///` to use the local filesystem), and set `dataPath` to point to the 
location where data files will be written out (eg `fs-data` will create a folder called `fs-data` in the current path).

The server ports are configured in `dropwizard-config.yml` - the default is to use port 8088

Start the server by running `./start-server.sh`

In the client, you will need to configure it to point to the local server by doing
```
import featurestream as fs
fs.set_endpoint('http://localhost:8088/featurestream/api')
```
You will need to do this each time you reload the featurestream library.



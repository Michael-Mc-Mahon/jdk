import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

/**
 * @test
 * @bug 4662246
 * @summary  REGRESSION: plugin 14x client authentication dialog returns NullPointerException
 * @run main/othervm ZipFileHttpServer
 */
public class ZipFileHttpServer {
    public static void main(String[] args) throws Exception {
        Path zip = Path.of("/Users/mimcmah/site.zip").toAbsolutePath();

        //URI zipUri = URI.create("jar:" + zip.toUri());
        URI zipUri = zip.toUri();
	System.out.println("zipuri = " + zipUri);

        FileSystem zipFs = FileSystems.newFileSystem(
                zipUri,
                Map.of("create", "false")
        );

        Path zipRoot = zipFs.getPath("/");

        var server = SimpleFileServer.createFileServer(new InetSocketAddress("localhost", 8080),
                zipRoot, SimpleFileServer.OutputLevel.VERBOSE);

        server.start();

        System.out.println("Serving " + zip + " at http://localhost:8080/");
        Thread.sleep(1000000);
    }
}

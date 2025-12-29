import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", e -> serveFile(e, "static/index.html"));
        server.createContext("/static", e -> serveDir(e, "static"));
        server.createContext("/data", e -> serveDir(e, "data"));

        server.setExecutor(null);
        server.start();
        System.out.println("Server running at http://localhost:8080");
    }

    static void serveFile(com.sun.net.httpserver.HttpExchange e, String path) throws IOException {
        File f = new File(path);
        byte[] bytes = Files.readAllBytes(f.toPath());
        e.getResponseHeaders().add("Content-Type", contentType(path));
        e.sendResponseHeaders(200, bytes.length);
        e.getResponseBody().write(bytes);
        e.close();
    }

    static void serveDir(com.sun.net.httpserver.HttpExchange e, String base) throws IOException {
        String path = base + e.getRequestURI().getPath().replace("/" + base, "");
        serveFile(e, path);
    }

    static String contentType(String p) {
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".json")) return "application/json";
        return "text/plain";
    }
}

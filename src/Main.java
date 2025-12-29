import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.*;

public class Main {

    static Map<String, Map<String, Boolean>> caught = new HashMap<>();

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/", e -> serveFile(e, "static/index.html"));
        server.createContext("/static", e -> serveDir(e, "static"));

        server.createContext("/api/state", Main::handleState);
        server.createContext("/api/pokemon", Main::handlePokemon);

        server.start();
        System.out.println("Server running on port " + port);
    }

    /* ================= API ================= */

    static void handleState(HttpExchange e) throws IOException {
        if (!e.getRequestMethod().equals("GET")) {
            e.sendResponseHeaders(405, -1);
            return;
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"caught\":{");

        boolean firstPokemon = true;
        for (var entry : caught.entrySet()) {
            if (!firstPokemon) json.append(",");
            firstPokemon = false;

            Map<String, Boolean> s = entry.getValue();
            json.append("\"").append(entry.getKey()).append("\":")
                .append("{\"normal\":").append(s.getOrDefault("normal", false))
                .append(",\"shiny\":").append(s.getOrDefault("shiny", false))
                .append("}");
        }

        json.append("}}");

        byte[] out = json.toString().getBytes();
        e.getResponseHeaders().add("Content-Type", "application/json");
        e.sendResponseHeaders(200, out.length);
        e.getResponseBody().write(out);
        e.close();
    }

    static void handlePokemon(HttpExchange e) throws IOException {
        if (!e.getRequestMethod().equals("PUT")) {
            e.sendResponseHeaders(405, -1);
            return;
        }

        String name = e.getRequestURI().getPath().replace("/api/pokemon/", "");

        String body = new String(e.getRequestBody().readAllBytes());
        boolean normal = body.contains("\"normal\":true");
        boolean shiny = body.contains("\"shiny\":true");

        Map<String, Boolean> state = new HashMap<>();
        state.put("normal", normal);
        state.put("shiny", shiny);
        caught.put(name, state);

        e.sendResponseHeaders(204, -1);
        e.close();
    }

    /* ================= STATIC ================= */

    static void serveFile(HttpExchange e, String path) throws IOException {
        File f = new File(path);
        if (!f.exists() || f.isDirectory()) {
            e.sendResponseHeaders(404, -1);
            e.close();
            return;
        }
        byte[] bytes = Files.readAllBytes(f.toPath());
        e.getResponseHeaders().add("Content-Type", contentType(path));
        e.sendResponseHeaders(200, bytes.length);
        e.getResponseBody().write(bytes);
        e.close();
    }

    static void serveDir(HttpExchange e, String base) throws IOException {
        String p = e.getRequestURI().getPath().substring(base.length() + 1);
        serveFile(e, base + "/" + p);
    }

    static String contentType(String p) {
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".json")) return "application/json";
        return "text/plain";
    }
}

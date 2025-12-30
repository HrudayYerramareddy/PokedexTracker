import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class Main {

    // In-memory cache (fast)
    static Map<String, Map<String, Boolean>> caught = new HashMap<>();

    // File persistence (survives Ctrl+C + restart)
    static final File SAVE_FILE = new File("data/caught.json");

    public static void main(String[] args) throws Exception {
        // Load saved state on boot
        loadCaughtFromDisk();

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/", e -> serveFile(e, "static/index.html"));
        server.createContext("/static", e -> serveDir(e, "static"));

        server.createContext("/api/state", Main::handleState);
        server.createContext("/api/pokemon", Main::handlePokemon);

        server.start();
        System.out.println("Server running on port " + port);

        // Save one last time on shutdown (Ctrl+C triggers this)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                saveCaughtToDisk();
                System.out.println("Saved state to " + SAVE_FILE.getPath());
            } catch (Exception ex) {
                System.out.println("Failed to save on shutdown: " + ex.getMessage());
            }
        }));
    }

    /* ================= API ================= */

    static void handleState(HttpExchange e) throws IOException {
        if (!e.getRequestMethod().equals("GET")) {
            e.sendResponseHeaders(405, -1);
            e.close();
            return;
        }

        byte[] out = buildStateJson().getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        e.sendResponseHeaders(200, out.length);
        e.getResponseBody().write(out);
        e.close();
    }

    static void handlePokemon(HttpExchange e) throws IOException {
        if (!e.getRequestMethod().equals("PUT")) {
            e.sendResponseHeaders(405, -1);
            e.close();
            return;
        }

        // /api/pokemon/<name>
        String path = e.getRequestURI().getPath();
        String name = path.replace("/api/pokemon/", "");
        name = URLDecoder.decode(name, StandardCharsets.UTF_8);

        String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        // Your existing simple parsing (kept)
        boolean normal = body.contains("\"normal\":true");
        boolean shiny = body.contains("\"shiny\":true");

        Map<String, Boolean> state = new HashMap<>();
        state.put("normal", normal);
        state.put("shiny", shiny);
        caught.put(name, state);

        // Persist immediately so even a crash won't lose it
        saveCaughtToDisk();

        e.sendResponseHeaders(204, -1);
        e.close();
    }

    /* ================= PERSISTENCE ================= */

    static String buildStateJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"caught\":{");

        boolean firstPokemon = true;
        for (var entry : caught.entrySet()) {
            if (!firstPokemon) json.append(",");
            firstPokemon = false;

            Map<String, Boolean> s = entry.getValue();
            json.append("\"").append(escapeJson(entry.getKey())).append("\":")
                .append("{\"normal\":").append(s.getOrDefault("normal", false))
                .append(",\"shiny\":").append(s.getOrDefault("shiny", false))
                .append("}");
        }

        json.append("}}");
        return json.toString();
    }

    static void saveCaughtToDisk() throws IOException {
        // Ensure folder exists
        File parent = SAVE_FILE.getParentFile();
        if (parent != null) parent.mkdirs();

        // Write to temp then rename (avoid corruption)
        File tmp = new File(SAVE_FILE.getPath() + ".tmp");
        Files.writeString(tmp.toPath(), buildStateJson(), StandardCharsets.UTF_8);

        // Best-effort atomic replace
        if (SAVE_FILE.exists() && !SAVE_FILE.delete()) {
            // if can't delete, try overwrite directly
            Files.writeString(SAVE_FILE.toPath(), buildStateJson(), StandardCharsets.UTF_8);
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(SAVE_FILE)) {
            // fallback: overwrite
            Files.writeString(SAVE_FILE.toPath(), buildStateJson(), StandardCharsets.UTF_8);
            tmp.delete();
        }
    }

    static void loadCaughtFromDisk() {
        try {
            if (!SAVE_FILE.exists()) return;

            String json = Files.readString(SAVE_FILE.toPath(), StandardCharsets.UTF_8);

            // Expect format: {"caught":{"pikachu":{"normal":true,"shiny":false}, ...}}
            int caughtIdx = json.indexOf("\"caught\"");
            if (caughtIdx < 0) return;

            int startObj = json.indexOf("{", caughtIdx);
            int endObj = json.lastIndexOf("}");
            if (startObj < 0 || endObj < 0 || endObj <= startObj) return;

            // Find the caught map object boundaries: after "caught":
            int caughtMapStart = json.indexOf("{", json.indexOf(":", caughtIdx));
            if (caughtMapStart < 0) return;

            // This is a simple parser for the exact JSON we write.
            // It will correctly restore what THIS server saved.
            String inner = json.substring(caughtMapStart + 1, json.lastIndexOf("}")); // inside caught:{ ... }
            inner = inner.trim();
            if (inner.isEmpty()) return;

            caught.clear();

            int i = 0;
            while (i < inner.length()) {
                // skip whitespace/commas
                while (i < inner.length() && (inner.charAt(i) == ' ' || inner.charAt(i) == '\n' || inner.charAt(i) == '\r' || inner.charAt(i) == '\t' || inner.charAt(i) == ',')) i++;
                if (i >= inner.length()) break;

                // key "name"
                if (inner.charAt(i) != '"') break;
                int keyEnd = findStringEnd(inner, i + 1);
                if (keyEnd < 0) break;
                String key = unescapeJson(inner.substring(i + 1, keyEnd));
                i = keyEnd + 1;

                // colon
                while (i < inner.length() && inner.charAt(i) != ':') i++;
                if (i >= inner.length()) break;
                i++;

                // object start
                while (i < inner.length() && inner.charAt(i) != '{') i++;
                if (i >= inner.length()) break;
                int objStart = i;
                int objEnd = findMatchingBrace(inner, objStart);
                if (objEnd < 0) break;

                String obj = inner.substring(objStart, objEnd + 1);

                boolean normal = obj.contains("\"normal\":true");
                boolean shiny = obj.contains("\"shiny\":true");

                Map<String, Boolean> state = new HashMap<>();
                state.put("normal", normal);
                state.put("shiny", shiny);
                caught.put(key, state);

                i = objEnd + 1;
            }

            System.out.println("Loaded saved state: " + caught.size() + " Pokémon");
        } catch (Exception ex) {
            System.out.println("Could not load saved state: " + ex.getMessage());
        }
    }

    static int findStringEnd(String s, int start) {
        // start points after the opening quote
        boolean esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') return i;
        }
        return -1;
    }

    static int findMatchingBrace(String s, int openIdx) {
        int depth = 0;
        boolean inString = false;
        boolean esc = false;

        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);

            if (inString) {
                if (esc) { esc = false; continue; }
                if (c == '\\') { esc = true; continue; }
                if (c == '"') inString = false;
                continue;
            } else {
                if (c == '"') { inString = true; continue; }
                if (c == '{') depth++;
                if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String unescapeJson(String s) {
        // Only needs to undo what escapeJson does
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
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
        if (p.endsWith(".html")) return "text/html; charset=utf-8";
        if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (p.endsWith(".json")) return "application/json; charset=utf-8";
        return "text/plain; charset=utf-8";
    }
}

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

class BuildLzaDex {

  // lines like "#001"
  private static final Pattern NUM_LINE = Pattern.compile("^#(\\d+)\\s*$");

  // very small JSON extractor: "id": 25
  private static final Pattern ID_FIELD = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

  // Special name -> PokéAPI species slug overrides
  private static final Map<String, String> SPECIAL = new HashMap<>();
  static {
    SPECIAL.put("Flabébé", "flabebe");
    SPECIAL.put("Farfetch'd", "farfetchd");
    SPECIAL.put("Sirfetch'd", "sirfetchd");
    SPECIAL.put("Mime Jr.", "mime-jr");
    SPECIAL.put("Mr. Mime", "mr-mime");
    SPECIAL.put("Mr. Rime", "mr-rime");
    SPECIAL.put("Type: Null", "type-null");
    SPECIAL.put("Nidoran♀", "nidoran-f");
    SPECIAL.put("Nidoran♂", "nidoran-m");
  }

  static class Entry {
    int num;           // LZA dex number (regional)
    String name;       // display name
    String apiName;    // pokeapi slug
    int speciesId;     // national dex id
    Entry(int num, String name, String apiName, int speciesId) {
      this.num = num; this.name = name; this.apiName = apiName; this.speciesId = speciesId;
    }
  }

  public static void main(String[] args) throws Exception {
    Path raw = Paths.get("data/lza_raw.txt");
    if (!Files.exists(raw)) {
      System.err.println("Missing data/lza_raw.txt (paste your LZA dex text there).");
      System.exit(1);
    }

    List<String> lines = Files.readAllLines(raw, StandardCharsets.UTF_8);

    // We will split into 2 sections:
    // - base: from the top until the second dex starts (your paste shows a new list starting at "Mankey")
    // - dlc: from that point onward
    List<String> baseLines = new ArrayList<>();
    List<String> dlcLines = new ArrayList<>();

    boolean inDlc = false;
    for (String ln : lines) {
      String t = ln.trim();
      if (t.isEmpty()) continue;

      // Heuristic: your DLC list starts with "Mankey #001"
      if (!inDlc && t.equalsIgnoreCase("Mankey")) {
        inDlc = true;
      }

      if (inDlc) dlcLines.add(t);
      else baseLines.add(t);
    }

    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    List<Entry> base = parseDex(baseLines, client, "Base");
    List<Entry> dlc  = parseDex(dlcLines,  client, "DLC");

    // Write JSON: data/lza.json
    Path out = Paths.get("data/lza.json");
    String json = buildJson(base, dlc);
    Files.writeString(out, json, StandardCharsets.UTF_8);

    System.out.println("Wrote " + out.toAbsolutePath());
    System.out.println("Base entries: " + base.size());
    System.out.println("DLC entries: " + dlc.size());
  }

  private static List<Entry> parseDex(List<String> lines, HttpClient client, String label) throws Exception {
    List<Entry> out = new ArrayList<>();

    // Format in your paste is:
    // Name
    // #001
    // Name
    // Type line (ignored)
    //
    // We'll parse whenever we see: Name then "#num"
    for (int i = 0; i < lines.size() - 1; i++) {
      String nameLine = lines.get(i).trim();
      Matcher m = NUM_LINE.matcher(lines.get(i + 1).trim());
      if (!m.matches()) continue;

      int num = Integer.parseInt(m.group(1));

      // We take the first name line as the display name
      String displayName = nameLine;

      String apiName = toApiName(displayName);
      int speciesId = fetchSpeciesId(client, apiName);

      out.add(new Entry(num, displayName, apiName, speciesId));
    }

    // Ensure sorted by regional number
    out.sort(Comparator.comparingInt(e -> e.num));

    // sanity print small warning if empty
    if (out.isEmpty()) {
      System.err.println("WARNING: parsed 0 entries for " + label + ". Check formatting in lza_raw.txt");
    }
    return out;
  }

  private static String toApiName(String display) {
    if (SPECIAL.containsKey(display)) return SPECIAL.get(display);

    // Normalize accents (quick+simple): remove non-ascii by hand for common ones
    String s = display
        .replace("é", "e")
        .replace("É", "e")
        .replace("’", "")
        .replace("'", "")
        .replace(".", "")
        .replace(":", "")
        .trim()
        .toLowerCase(Locale.ROOT);

    // spaces -> hyphens
    s = s.replaceAll("\\s+", "-");

    // keep letters, digits, hyphen only
    s = s.replaceAll("[^a-z0-9\\-]", "");

    return s;
  }

  private static int fetchSpeciesId(HttpClient client, String apiName) throws Exception {
    // Use pokemon-species endpoint; id == national dex number
    // https://pokeapi.co/api/v2/pokemon-species/{apiName}
    String url = "https://pokeapi.co/api/v2/pokemon-species/" + apiName;
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();

    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (res.statusCode() != 200) {
      throw new RuntimeException("Failed fetching species for '" + apiName + "' (" + res.statusCode() + "): " + url);
    }

    Matcher m = ID_FIELD.matcher(res.body());
    if (!m.find()) throw new RuntimeException("Could not find id field in response for " + apiName);
    return Integer.parseInt(m.group(1));
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String buildJson(List<Entry> base, List<Entry> dlc) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"gameId\": \"gen10-lza\",\n");
    sb.append("  \"sections\": [\n");

    sb.append("    {\n");
    sb.append("      \"id\": \"kalos-base\",\n");
    sb.append("      \"name\": \"Kalos\",\n");
    sb.append("      \"pokemon\": [\n");
    for (int i = 0; i < base.size(); i++) {
      Entry e = base.get(i);
      sb.append("        { \"num\": ").append(e.num)
        .append(", \"name\": \"").append(escape(e.name)).append("\"")
        .append(", \"apiName\": \"").append(escape(e.apiName)).append("\"")
        .append(", \"speciesId\": ").append(e.speciesId).append(" }");
      sb.append(i == base.size() - 1 ? "\n" : ",\n");
    }
    sb.append("      ]\n");
    sb.append("    },\n");

    sb.append("    {\n");
    sb.append("      \"id\": \"mega-dimension\",\n");
    sb.append("      \"name\": \"Mega Dimension\",\n");
    sb.append("      \"pokemon\": [\n");
    for (int i = 0; i < dlc.size(); i++) {
      Entry e = dlc.get(i);
      sb.append("        { \"num\": ").append(e.num)
        .append(", \"name\": \"").append(escape(e.name)).append("\"")
        .append(", \"apiName\": \"").append(escape(e.apiName)).append("\"")
        .append(", \"speciesId\": ").append(e.speciesId).append(" }");
      sb.append(i == dlc.size() - 1 ? "\n" : ",\n");
    }
    sb.append("      ]\n");
    sb.append("    }\n");

    sb.append("  ]\n");
    sb.append("}\n");
    return sb.toString();
  }
}

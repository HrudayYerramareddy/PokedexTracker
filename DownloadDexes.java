import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DownloadDexes {

  private static final HttpClient client = HttpClient.newHttpClient();

  public static void main(String[] args) throws Exception {
    Path outDir = Path.of("dex_json");
    Files.createDirectories(outDir);

    // 1) list all pokedexes
    String listJson = get("https://pokeapi.co/api/v2/pokedex/?limit=2000");
    // crude parse: extract all "name":"..."
    Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    Matcher m = namePattern.matcher(listJson);

    int count = 0;
    while (m.find()) {
      String dexName = m.group(1);

      // 2) fetch each pokedex JSON
      String dexJson = get("https://pokeapi.co/api/v2/pokedex/" + dexName + "/");

      // 3) save
      Files.writeString(outDir.resolve(dexName + ".json"), dexJson, StandardCharsets.UTF_8);
      count++;
      System.out.println("Saved: " + dexName);
      Thread.sleep(150); // be nice to the API
    }

    System.out.println("Done. Saved " + count + " dex files into " + outDir.toAbsolutePath());
  }

  private static String get(String url) throws IOException, InterruptedException {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", "PokedexTracker/1.0")
        .GET()
        .build();
    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) {
      throw new IOException("GET failed " + res.statusCode() + " for " + url);
    }
    return res.body();
  }
}

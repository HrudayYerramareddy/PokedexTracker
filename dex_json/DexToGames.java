import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import java.util.*;

public class DexToGames {
  // Extracts pokemon species names from PokéAPI pokedex JSON
  // Looks for: "pokemon_species":{"name":"XYZ"
  private static final Pattern SPECIES_NAME =
      Pattern.compile("\"pokemon_species\"\\s*:\\s*\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"");

  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.out.println("Usage: java DexToGames <pokedex.json> <gameId> <gameDisplayName>");
      System.out.println("Example: java DexToGames galar.json swsh \"Sword/Shield\"");
      return;
    }

    String jsonPath = args[0];
    String gameId = args[1];
    String gameName = args[2];

    String json = Files.readString(Path.of(jsonPath), StandardCharsets.UTF_8);

    // preserve order; avoid duplicates
    LinkedHashSet<String> apiNames = new LinkedHashSet<>();
    Matcher m = SPECIES_NAME.matcher(json);
    while (m.find()) {
      apiNames.add(m.group(1));
    }

    if (apiNames.isEmpty()) {
      System.out.println("No pokemon_species names found. Check file content/format.");
      return;
    }

    System.out.println("const GAMES = [");
    System.out.println("  {");
    System.out.println("    id: \"" + escape(gameId) + "\",");
    System.out.println("    name: \"" + escape(gameName) + "\",");
    System.out.println("    pokemon: [");

    int i = 0;
    for (String api : apiNames) {
      String display = displayNameFromApi(api);
      System.out.print("      { name: \"" + escape(display) + "\", apiName: \"" + escape(api) + "\" }");
      i++;
      System.out.println(i < apiNames.size() ? "," : "");
    }

    System.out.println("    ]");
    System.out.println("  }");
    System.out.println("];");
  }

  // Turns "mr-mime" -> "Mr Mime", "jangmo-o" -> "Jangmo-o"
  // Keeps hyphen for special endings like "-o" by re-joining intelligently.
  private static String displayNameFromApi(String api) {
    // Special cases you might want to tweak later:
    // farfetchd -> Farfetchd (PokéAPI slug drops apostrophe)
    // type-null -> Type: Null (PokéAPI slug)
    // mime-jr -> Mime Jr
    if (api.equals("type-null")) return "Type: Null";
    if (api.equals("mime-jr")) return "Mime Jr";
    if (api.equals("mr-mime")) return "Mr Mime";
    if (api.equals("mr-rime")) return "Mr Rime";
    if (api.equals("farfetchd")) return "Farfetchd";
    if (api.equals("sirfetchd")) return "Sirfetchd";

    // Default: split on hyphen, Title Case each chunk, join with space
    String[] parts = api.split("-");
    for (int j = 0; j < parts.length; j++) {
      parts[j] = titleCase(parts[j]);
    }

    // Keep some hyphenated forms looking nicer:
    // e.g. "Jangmo-o", "Hakamo-o", "Kommo-o"
    if (api.endsWith("-o") && parts.length >= 2) {
      // Join all but last with space, then append "-O" style
      StringBuilder sb = new StringBuilder();
      for (int k = 0; k < parts.length - 1; k++) {
        if (k > 0) sb.append(" ");
        sb.append(parts[k]);
      }
      sb.append("-").append(parts[parts.length - 1]); // last chunk already TitleCased
      return sb.toString();
    }

    return String.join(" ", parts);
  }

  private static String titleCase(String s) {
    if (s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}

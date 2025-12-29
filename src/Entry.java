public class Entry {
  public boolean normal;
  public boolean shiny;

  public static Entry fromJson(String json) {
    boolean n = json.contains("\"normal\":true");
    boolean s = json.contains("\"shiny\":true");
    if (s) n = true;
    if (!n) s = false;
    Entry e = new Entry();
    e.normal = n;
    e.shiny = s;
    return e;
  }

  public String toJson() {
    return "{\"normal\":" + normal + ",\"shiny\":" + shiny + "}";
  }
}

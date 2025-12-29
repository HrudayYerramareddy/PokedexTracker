import java.nio.file.*;
import java.util.*;

public class StateStore {
  private final Path file = Path.of("data/state.json");
  private AppState state = new AppState();

  public StateStore(){ load(); }

  public AppState get(){ return state; }

  public void patch(String json){
    String g = extract(json,"activeGameId");
    if(g!=null) state.activeGameId=g;
    String game = extract(json,"gameId");
    String view = extract(json,"activeView");
    if(game!=null && view!=null) state.activeViewByGame.put(game,view);
    save();
  }

  public void setEntry(String game,String id,Entry e){
    state.progress.putIfAbsent(game,new HashMap<>());
    state.progress.get(game).put(id,e);
    save();
  }

  public void resetGame(String g){
    state.progress.remove(g);
    state.activeViewByGame.remove(g);
    save();
  }

  private void load(){
    try{
      if(!Files.exists(file)) save();
    }catch(Exception ignored){}
  }

  private void save(){
    try{
      Files.createDirectories(file.getParent());
      Files.writeString(file,state.toJson());
    }catch(Exception e){throw new RuntimeException(e);}
  }

  private String extract(String json,String key){
    int i=json.indexOf("\""+key+"\"");
    if(i==-1) return null;
    int s=json.indexOf("\"",json.indexOf(":",i))+1;
    int e=json.indexOf("\"",s);
    return json.substring(s,e);
  }
}

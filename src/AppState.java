import java.util.*;

public class AppState {
  public String activeGameId;
  public Map<String,String> activeViewByGame = new HashMap<>();
  public Map<String,Map<String,Entry>> progress = new HashMap<>();

  public String toJson() {
    StringBuilder sb = new StringBuilder("{");
    sb.append("\"activeGameId\":").append(activeGameId==null?"null":"\""+activeGameId+"\"").append(",");
    sb.append("\"activeViewByGame\":").append(map(activeViewByGame)).append(",");
    sb.append("\"progress\":").append(progress());
    sb.append("}");
    return sb.toString();
  }

  private String map(Map<String,String> m){
    StringBuilder sb=new StringBuilder("{");
    int i=0;
    for(var e:m.entrySet()){
      if(i++>0) sb.append(",");
      sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
    }
    return sb.append("}").toString();
  }

  private String progress(){
    StringBuilder sb=new StringBuilder("{");
    int g=0;
    for(var game:progress.entrySet()){
      if(g++>0) sb.append(",");
      sb.append("\"").append(game.getKey()).append("\":{");
      int p=0;
      for(var mon:game.getValue().entrySet()){
        if(p++>0) sb.append(",");
        sb.append("\"").append(mon.getKey()).append("\":").append(mon.getValue().toJson());
      }
      sb.append("}");
    }
    return sb.append("}").toString();
  }
}

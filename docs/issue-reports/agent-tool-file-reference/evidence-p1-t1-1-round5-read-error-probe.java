import com.et.agent.AgentLoop;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public class AtfrReadErrorProbe {
 public static void main(String[] x) {
  AtomicInteger count=new AtomicInteger();
  AgentLoop.Model model=(history,delta,cancelled)->{
   if(count.getAndIncrement()==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("c","list_writable_wiki_locations","{}")),null);
   System.out.println("toolResult="+history.get(history.size()-1).content);
   delta.accept("Read failed. Please check access."); return new AgentLoop.Turn(List.of(),null);
  };
  Map<String,AgentLoop.Tool> tools=Map.of("list_writable_wiki_locations",a->"{\"code\":40101,\"message\":\"request rejected\"}","plan_wiki_destination",a->"{}");
  List<String> output=new ArrayList<>();
  AgentLoop.Result result=new AgentLoop(model,Map.of(),3,10000,10000).run("list",List.of(),null,null,tools,output::add,()->false);
  System.out.println("modelFinalText=Read failed. Please check access.; emitted="+output+"; events="+result.wikiEvents.size()+"; choice="+result.choiceRequest);
 }
}
import com.et.agent.AgentLoop;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public class Probe {
 public static void main(String[] x) {
  AtomicInteger count = new AtomicInteger();
  AgentLoop.Model model = (history, delta, cancelled) -> {
    if(count.getAndIncrement()==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("c", "list_writable_wiki_locations", "{}")),null);
    delta.accept("Found locations"); return new AgentLoop.Turn(List.of(),null);
  };
  Map<String, AgentLoop.Tool> tools = Map.of("list_writable_wiki_locations", a -> "{\"spaces\":[]}","plan_wiki_destination",a -> "{}");
  List<String> text = new ArrayList<>();
  AgentLoop loop = new AgentLoop(model,Map.of(),3,10000,10000);
  AgentLoop.Result result=loop.run("list locations",List.of(),null,null,tools,text::add,()->false);
  System.out.println("modelFinalText=Found locations; emittedText="+text+"; wikiEvents="+result.wikiEvents.size()+"; choice="+result.choiceRequest);
 }
}

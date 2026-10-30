import com.et.agent.*;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
final class ProbeTask12 {
 static void run(String label,String input) {
  AtomicInteger calls=new AtomicInteger(), turns=new AtomicInteger();
  AgentLibraryGateway fake=(AgentLibraryGateway)Proxy.newProxyInstance(AgentLibraryGateway.class.getClassLoader(),new Class[]{AgentLibraryGateway.class},(p,m,a)->{calls.incrementAndGet();return "{\"saved\":true}";});
  AgentLoop.Model model=(h,d,c)->{if(turns.getAndIncrement()==0)return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1","write_file",input)),null); System.out.println("tool_result="+h.get(h.size()-1).content);return new AgentLoop.Turn(List.of(),null);};
  System.out.println("case="+label+" input="+input);
  try{new AgentLoop(model,AgentToolRegistry.bind("7",fake),3,10000,10000).run("q",x->{},()->false);System.out.println("loop=completed");}catch(Exception e){System.out.println("exception="+e.getClass().getName()+" message="+e.getMessage());}
  System.out.println("fake_gateway_calls="+calls.get());
 }
 public static void main(String[] a){System.out.println("Gateway is an in-memory proxy; no actual write or network access.");run("root-array","[]");run("trailing-invalid-json","{\"path\":\"x\",\"content\":\"x\",\"mode\":\"create\"} trailing");}
}
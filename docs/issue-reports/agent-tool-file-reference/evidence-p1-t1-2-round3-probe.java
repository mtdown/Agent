import com.et.agent.*;
import com.et.cloud.controller.AgentSkillDefinitionValidator;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
public class AgentSkillStoreProbe {
 static org.springframework.jdbc.core.JdbcTemplate jdbc;
 static JsonNode validateStore(String s) throws Exception {
  com.et.cloud.controller.AgentLibraryStore store = new com.et.cloud.controller.AgentLibraryStore(org.mockito.Mockito.mock(javax.sql.DataSource.class), org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class), new AgentSkillDefinitionValidator(), new com.et.cloud.controller.AgentCodeVersionValidator());
  java.lang.reflect.Field f = store.getClass().getDeclaredField("jdbc"); f.setAccessible(true); f.set(store,jdbc);
  java.lang.reflect.Method m=store.getClass().getDeclaredMethod("validateSkill",long.class,String.class); m.setAccessible(true); try {return (JsonNode)m.invoke(store,7L,s);} catch(java.lang.reflect.InvocationTargetException e){throw (RuntimeException)e.getCause();}
 }
 static String def(String version, String min, String max) { return "{\"name\":\"a\",\"summary\":\"a\",\"when_to_use\":\"a\",\"when_not_to_use\":\"a\",\"parameters\":[{\"name\":\"n\",\"type\":\"number\",\"required\":true,\"min\":"+min+",\"max\":"+max+"}],\"steps\":[{\"type\":\"code\",\"code_item_id\":\"1\",\"code_version\":"+version+",\"entrypoint\":\"main\",\"args\":{\"nested\":[1.00000000000000001,1e-20,123456789012345678901234567890]}}],\"expected_results\":[\"ok\"],\"failure_policy\":\"stop\",\"examples\":[{\"input\":{\"n\":1.00000000000000001},\"expected\":\"ok\"}]}"; }
 public static void main(String[] ignored) {
  AtomicInteger calls = new AtomicInteger();
  jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
  org.mockito.Mockito.when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.<Object[]>any())).thenAnswer(inv -> { Object[] args=inv.getArguments(); if(!args[3].equals(1)) throw new AssertionError("wrong version " + args[3]); return 1; });
  AgentLibraryGateway gateway=(AgentLibraryGateway)Proxy.newProxyInstance(AgentLibraryGateway.class.getClassLoader(),new Class[]{AgentLibraryGateway.class},(p,m,a)->{
   if(!m.getName().equals("createSkill")) throw new AssertionError(m.getName()); calls.incrementAndGet();
   JsonNode parsed=validateStore((String)a[1]);
   if(!parsed.path("steps").get(0).path("code_version").isInt()) throw new AssertionError("version");
   if(parsed.path("parameters").get(0).path("min").decimalValue().compareTo(new BigDecimal("1")) != 0 || parsed.path("parameters").get(0).path("max").decimalValue().compareTo(new BigDecimal("1.00000000000000001")) != 0) throw new AssertionError("bounds precision");
   JsonNode nested=parsed.path("steps").get(0).path("args").path("nested");
   if(nested.get(0).decimalValue().compareTo(new BigDecimal("1.00000000000000001"))!=0 || nested.get(1).decimalValue().compareTo(new BigDecimal("1e-20"))!=0 || !nested.get(2).bigIntegerValue().toString().equals("123456789012345678901234567890")) throw new AssertionError("precision");
   if(parsed.path("examples").get(0).path("input").path("n").decimalValue().compareTo(new BigDecimal("1.00000000000000001"))!=0) throw new AssertionError("input precision");
   return "fake";
  });
  AgentLoop.Tool tool=AgentToolRegistry.bind("7",gateway).get("create_skill");
  for(String version:new String[]{"1.0","1e0"}) { tool.execute("{\"definition\":"+def(version,"1","1.00000000000000001")+"}"); System.out.println("PASS valid version="+version+" precise bounds and free objects"); }
  try { tool.execute("{\"definition\":"+def("1.0","1.00000000000000001","1")+"}"); throw new AssertionError("inverted bounds accepted"); } catch(IllegalArgumentException expected) { if(!expected.getMessage().contains("min exceeds")) throw expected; System.out.println("PASS inverted precise bounds rejected by Cloud"); }
  for(String version:new String[]{"1.1","2147483648","1.00000000000000001"}) { int before=calls.get(); try { tool.execute("{\"definition\":"+def(version,"1","2")+"}"); throw new AssertionError("version accepted"); } catch(IllegalArgumentException expected) { if(calls.get()!=before) throw new AssertionError("gateway accessed"); System.out.println("PASS invalid version="+version+" no gateway"); } }
 }
}
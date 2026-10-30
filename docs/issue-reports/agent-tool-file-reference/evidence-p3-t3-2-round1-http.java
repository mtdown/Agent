package com.et.cloud.controller;
import java.nio.file.*; import java.net.*; import java.net.http.*; import java.util.*;
import javax.servlet.http.HttpServletRequest;
import org.springframework.boot.*; import org.springframework.boot.autoconfigure.*; import org.springframework.boot.autoconfigure.web.servlet.*; import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration; import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration; import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext; import org.springframework.context.annotation.*; import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import com.et.cloud.exception.*; import com.et.cloud.model.entity.User; import com.et.cloud.service.UserService; import com.fasterxml.jackson.databind.*; import static org.mockito.Mockito.*; import static org.mockito.ArgumentMatchers.*;
@SpringBootConfiguration @EnableWebMvc @Import({ServletWebServerFactoryAutoConfiguration.class,DispatcherServletAutoConfiguration.class,JacksonAutoConfiguration.class,HttpMessageConvertersAutoConfiguration.class})
public class HttpProbe {
 static AgentPreviewController controller;
 @Bean AgentPreviewController controller() {return controller;} @Bean GlobalExceptionHandler advice(){return new GlobalExceptionHandler();}
 public static void main(String[] args) throws Exception {
  Path root=Files.createTempDirectory("probe-files-"); String secret="synthetic-secret-32"; long owner=111, stranger=222; String body="SYNTHETIC_PRIVATE_CONTENT_"+"x".repeat(20000);
  var files=new AgentPersonalFileStore(root); files.write(owner,"probe.txt",body,"create"); var store=mock(AgentConversationStore.class);
  doAnswer(inv->{long u=inv.getArgument(0); if(u!=owner) throw new IllegalArgumentException("Conversation not found"); return null;}).when(store).requireOwner(anyLong(),anyLong());
  var refs=new AgentPersonalFileReferences(files,store,secret); var collect=refs.collect(owner); String evidence=AgentPersonalFileEvidence.issue(secret,owner,"probe.txt",files.version(owner,"probe.txt"),0,10); var json=new ObjectMapper(); collect.accept(json.valueToTree(List.of(evidence))); String stats=collect.stats("{}"); var node=json.readTree(stats).path("personalFileReferencesInternal").get(0); String token=node.path("token").asText();
  when(store.messagesRaw(eq(owner),eq(1L))).thenReturn(List.of(Map.of("id",2,"role","assistant","status","DONE","statsJson",stats)));
  var users=mock(UserService.class); when(users.getLoginUser(any(HttpServletRequest.class))).thenAnswer(inv->{HttpServletRequest req=inv.getArgument(0); String h=req.getHeader("X-Test-User"); if(h==null) throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR); User u=new User(); u.setId(Long.parseLong(h)); return u;});
  controller=new AgentPreviewController(users,null,store,null,null,null,null,refs);
  SpringApplication app=new SpringApplication(HttpProbe.class); app.setDefaultProperties(Map.of("server.port","0","server.address","127.0.0.1","spring.main.banner-mode","off")); var ctx=(ServletWebServerApplicationContext)app.run("--server.port=0", "--server.servlet.context-path=/", "--spring.config.location=optional:file:./no-such-probe-config/"); int port=ctx.getWebServer().getPort(); var client=HttpClient.newHttpClient(); String base="http://127.0.0.1:"+port+"/agent/conversations/1/messages/2/personal-file-reference/preview?token=";
  try {
   String good=request(client,base+token,"111"); JsonNode initial=json.readTree(good); check(initial.path("code").asInt()==0 && initial.path("data").path("text").asText().contains("SYNTHETIC_PRIVATE_CONTENT"),"initial signed reference"); String cursor=initial.path("data").path("next").asText(); check(!cursor.isEmpty(),"valid pagination cursor");
   denied(client,base+token,null,"logout",root,body,secret,evidence,token,cursor); denied(client,base+token,"222","cross-user",root,body,secret,evidence,token,cursor);
   denied(client,base+UUID.randomUUID(),"111","forged reference",root,body,secret,evidence,token,cursor); denied(client,base+token+"&cursor=forged","111","forged cursor",root,body,secret,evidence,token,cursor);
   files.write(owner,"probe.txt","SYNTHETIC_CHANGED_CONTENT","overwrite"); denied(client,base+token+"&cursor="+cursor,"111","changed file",root,body,secret,evidence,token,cursor);
   Files.delete(root.resolve("111").resolve("probe.txt")); denied(client,base+token,"111","deleted file",root,body,secret,evidence,token,cursor);
  } finally {ctx.close(); Files.deleteIfExists(root.resolve("111")); Files.deleteIfExists(root);}
 }
 static String request(HttpClient c,String url,String user)throws Exception {var b=HttpRequest.newBuilder(URI.create(url)).GET(); if(user!=null)b.header("X-Test-User",user); var r=c.send(b.build(),HttpResponse.BodyHandlers.ofString()); check(r.statusCode()==200,"HTTP status "+r.statusCode()); return r.body();}
 static void denied(HttpClient c,String url,String user,String label,Path root,String body,String secret,String evidence,String token,String cursor)throws Exception {String out=request(c,url,user); JsonNode n=new ObjectMapper().readTree(out); check(n.path("code").asInt()!=0 && n.path("data").isNull() && !n.path("message").asText().isBlank(),label+" error response"); for(String s:List.of(root.toString(),root.toString().replace('\\','/'),"SYNTHETIC_PRIVATE_CONTENT","SYNTHETIC_CHANGED_CONTENT",secret,evidence,token,cursor)) check(!out.contains(s),label+" no sensitive value"); System.out.println(label+" code="+n.path("code")+" message="+n.path("message"));}
 static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
}

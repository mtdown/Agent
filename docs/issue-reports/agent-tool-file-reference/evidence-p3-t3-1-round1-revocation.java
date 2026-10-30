package com.et.cloud.controller;
import com.et.cloud.model.constant.UserConstant;
import com.et.cloud.model.entity.User;
import com.et.cloud.service.impl.UserServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.apache.catalina.startup.Tomcat;
import javax.servlet.http.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.io.*;
public class RevocationProbe {
 static class Users extends UserServiceImpl { public User getById(java.io.Serializable id) { User u=new User(); u.setId(123L); return u; } }
 static class Conversations extends AgentConversationStore {
  Map<String,Object> row;
  Conversations(){super(new DriverManagerDataSource(),null,null);}
  public void requireOwner(long owner,long conversation){if(owner!=123 || conversation!=45) throw new IllegalArgumentException();}
  List<Map<String,Object>> messagesRaw(long owner,long conversation){requireOwner(owner,conversation);return List.of(row);}
 }
 public static void main(String[] args) throws Exception {
  Path dir=Files.createTempDirectory(Path.of(args[0]),"files-");
  AgentPersonalFileStore files=new AgentPersonalFileStore(dir); files.write(123,"private.txt","secret-probe-body","create");
  Conversations conversations=new Conversations(); AgentPersonalFileReferences refs=new AgentPersonalFileReferences(files,conversations,"probe-key");
  var ev=new AgentResourceInternalController(files,"probe-key").readSegment("probe-key","123","private.txt",null).getData().get("referenceEvidence");
  var collector=refs.collect(123); ObjectMapper json=new ObjectMapper(); collector.accept(json.valueToTree(ev));
  String stats=collector.stats("{}"); String token=json.readTree(stats).path("personalFileReferencesInternal").get(0).path("token").asText();
  conversations.row=Map.of("id",67,"role","assistant","status","DONE","statsJson",stats);
  Users users=new Users(); AgentPreviewController controller=new AgentPreviewController(users,null,conversations,null,null,null,null,refs);
  Tomcat tomcat=new Tomcat(); tomcat.setBaseDir(Path.of(args[0],"tomcat").toString()); tomcat.setPort(0); tomcat.getConnector();
  var context=tomcat.addContext("",Path.of(args[0]).toString());
  Tomcat.addServlet(context,"probe",new HttpServlet(){protected void doGet(HttpServletRequest req,HttpServletResponse resp) throws IOException {
    try { switch(req.getRequestURI()) {
     case "/login": User u=new User();u.setId(123L);req.getSession().setAttribute(UserConstant.USER_LOGIN_STATE,u);resp.getWriter().write("login");break;
     case "/logout": users.userLogout(req);resp.getWriter().write("logout");break;
     case "/ref": resp.setContentType("application/json");resp.getWriter().write(json.writeValueAsString(controller.personalFileReference("45","67",token,req)));break;
     default:resp.setStatus(404);
    }} catch(Exception ex){resp.setStatus(401);resp.getWriter().write("denied:"+ex.getClass().getSimpleName());}
  }}); context.addServletMappingDecoded("/*","probe");
  try { tomcat.start(); int port=tomcat.getConnector().getLocalPort(); CookieManager cookies=new CookieManager(); HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).build();
   java.util.function.Function<String,HttpResponse<String>> get=(path)->{try{return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}catch(Exception e){throw new RuntimeException(e);}};
   var login=get.apply("/login"); var before=get.apply("/ref"); var logout=get.apply("/logout"); var after=get.apply("/ref");
   if(login.statusCode()!=200 || before.statusCode()!=200 || !before.body().contains("private.txt") || logout.statusCode()!=200 || after.statusCode()!=401 || after.body().contains("secret-probe-body") || after.body().contains(dir.toString()) || after.body().contains("private.txt")) throw new AssertionError("probe assertions failed");
   System.out.println("login=200; owner-reference-before=200; logout=200; same-cookie-reference-after=401; denied-body="+after.body()+"; leaked-content=false; leaked-path=false; PASS");
  } finally { tomcat.stop();tomcat.destroy(); }
 }
}
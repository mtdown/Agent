package com.et.cloud.controller;
import com.et.cloud.model.entity.User;
import com.et.cloud.service.impl.AgentWikiCreationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.AfterTransaction;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE) @ActiveProfiles("local") @Transactional
class Round4SupplementProbeTest {
 @Autowired JdbcTemplate jdbc; @Autowired AgentWikiChoiceService choices; @Autowired AgentWikiCreationService creation; @Autowired AgentConversationStore conversations;
 final String account="r4-probe-"+UUID.randomUUID(); final ObjectMapper json=new ObjectMapper();
 void event(AgentWikiChoiceProjection p,String name,Object result) throws Exception {p.project("wiki_tool_event",json.readTree("{\"name\":\""+name+"\",\"result\":"+json.writeValueAsString(result)+"}"));}
 @Test void verifyPairingAndObservations() throws Exception {
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)",account,"test-only","user");
  User u=new User(); u.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
  jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)","Probe",u.getId()); String space=jdbc.queryForObject("SELECT LAST_INSERT_ID()",String.class);
  var a=creation.createFolder(u.getId(),space,"FolderAlpha"); var b=creation.createFolder(u.getId(),space,"FolderBeta");
  for(boolean same:new boolean[]{false,true}) {
   var d1=creation.createDocument(u.getId(),space,a.folderId,same?"SameOne":"CrossOne", "BodyOne");
   var d2=creation.createDocument(u.getId(),space,same?a.folderId:b.folderId,same?"SameTwo":"CrossTwo", "BodyTwo");
   long c=conversations.create(u.getId()); long t=conversations.begin(u.getId(),c,"probe","OFF","{}");
   var p=new AgentWikiChoiceProjection(choices,u,c,t); event(p,"create_wiki_folder",a); event(p,"create_wiki_folder",b); event(p,"create_wiki_document",d1); event(p,"create_wiki_document",d2);
   String internal=p.interactionJson(); var live=p.displayInteraction(internal);
   conversations.complete(u.getId(),c,t,p.displayAnswer(),"[]","{}",internal);
   var row=conversations.messages(u.getId(),c).stream().filter(x->"assistant".equals(x.get("role"))).findFirst().orElseThrow(); var history=(Map<?,?>)row.get("interaction");
   for(var display:List.of(live,history)) {
    var results=(List<?>)display.get("results"); assertEquals(4,results.size());
    for(int i=0;i<2;i++) {var item=(Map<?,?>)results.get(i); String title=i==0?(same?"SameOne":"CrossOne"):(same?"SameTwo":"CrossTwo"); String folder=i==0||same?a.name:b.name; String body=i==0?"BodyOne":"BodyTwo";
     assertEquals(title,item.get("documentTitle")); assertEquals(folder,item.get("folderName")); String token=(String)item.get("openToken"); assertNotNull(token); assertFalse(token.isBlank()); assertEquals(body,choices.preview(u,c,t,token).get("content"));
     System.out.println("PAIR " +(same?"same":"cross")+" "+title+" -> "+folder+" -> token -> "+body);
    }
    String shown=String.valueOf(display); assertFalse(shown.contains(d1.documentId)); assertFalse(shown.contains(d2.documentId));
   }
  }
  var p=new AgentWikiChoiceProjection(choices,u,1,2); event(p,"create_wiki_folder",a); System.out.println("FOLDER_ONLY="+p.displayAnswer()+" DISPLAY="+p.displayInteraction(p.interactionJson()));
  var f=new AgentWikiChoiceProjection(choices,u,1,2); event(f,"create_wiki_document",Map.of("saved",false)); event(f,"write_wiki_document",Map.of("status","blocked")); System.out.println("FAIL_INTERNAL="+f.interactionJson()); System.out.println("FAIL_PUBLIC="+f.displayInteraction(f.interactionJson())+" ANSWER="+f.displayAnswer());
 }
 @AfterTransaction void rollbackVerified(){assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE userAccount=?",Integer.class,account)); System.out.println("ROLLBACK VERIFIED account count=0");}
}

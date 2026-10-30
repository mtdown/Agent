package com.et.cloud.controller;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.constant.UserConstant;
import com.et.cloud.service.impl.AgentWikiCreationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local") @Transactional
class AgentWikiMultiResultLocalIT {
 @Autowired JdbcTemplate jdbc; @Autowired AgentWikiChoiceService choices; @Autowired AgentWikiCreationService creation; @Autowired AgentConversationStore conversations;
 @Autowired com.et.cloud.service.UserService users; @Autowired com.et.cloud.rag.RagSearchService search;
 @Autowired com.et.cloud.service.WikiSpaceService spaces; @Autowired AgentSkillCatalog catalog; @Autowired AgentLibraryStore library;
 @Test void eachConfirmedDocumentKeepsItsOwnFolder() throws Exception {
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","multi-it-"+UUID.randomUUID(),"test-only",UserConstant.DEFAULT_ROLE);
  User owner = new User(); owner.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
  jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)","测试",owner.getId());
  String space=jdbc.queryForObject("SELECT LAST_INSERT_ID()",String.class);
  var a=creation.createFolder(owner.getId(),space,"目录A"); var b=creation.createFolder(owner.getId(),space,"目录B");
  var first=creation.createDocument(owner.getId(),space,a.folderId,"文档甲","甲内容");
  var second=creation.createDocument(owner.getId(),space,b.folderId,"文档乙","乙内容");
  ObjectMapper json=new ObjectMapper(); AgentWikiChoiceProjection p=new AgentWikiChoiceProjection(choices,owner,11,12);
  p.project("wiki_tool_event",json.readTree("{\"name\":\"create_wiki_document\",\"result\":"+json.writeValueAsString(first)+"}"));
  p.project("wiki_tool_event",json.readTree("{\"name\":\"create_wiki_folder\",\"result\":"+json.writeValueAsString(b)+"}"));
  p.project("wiki_tool_event",json.readTree("{\"name\":\"create_wiki_document\",\"result\":"+json.writeValueAsString(second)+"}"));
  String answer=p.displayAnswer(); assertTrue(answer.contains("文档甲")); assertTrue(answer.contains("文档乙")); assertTrue(answer.contains(a.name)); assertTrue(answer.contains(b.name));
 }

 @Test void historyUsesCurrentSqlLocationsAndNeverLeaksAfterDeletionOrLoss() throws Exception {
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","multi-history-"+UUID.randomUUID(),"test-only",UserConstant.DEFAULT_ROLE);
  User owner = new User(); owner.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
  jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)","私人",owner.getId());
  String space=jdbc.queryForObject("SELECT LAST_INSERT_ID()",String.class);
  var a=creation.createFolder(owner.getId(),space,"原目录"); var b=creation.createFolder(owner.getId(),space,"新目录");
  var first=creation.createDocument(owner.getId(),space,a.folderId,"机密甲","正文甲");
  var second=creation.createDocument(owner.getId(),space,a.folderId,"机密乙","正文乙");
  long conversation=conversations.create(owner.getId()); long turn=conversations.begin(owner.getId(),conversation,"测试","OFF","{}");
  AgentWikiChoiceProjection p=new AgentWikiChoiceProjection(choices,owner,conversation,turn); ObjectMapper json=new ObjectMapper();
  p.project("wiki_tool_event",json.readTree("{\"name\":\"create_wiki_folder\",\"result\":"+json.writeValueAsString(a)+"}"));
  p.project("wiki_tool_event",json.readTree("{\"name\":\"create_wiki_folder\",\"result\":"+json.writeValueAsString(b)+"}"));
  for (var d : java.util.Arrays.asList(first,second)) p.project("wiki_tool_event",json.readTree("{\"name\":\"create_wiki_document\",\"result\":"+json.writeValueAsString(d)+"}"));
  p.project("wiki_tool_event",json.readTree("{\"name\":\"write_wiki_document\",\"result\":{\"status\":\"unknown\"}}"));
  String internal=p.interactionJson(); String answer=p.displayAnswer();
  assertTrue(answer.contains("机密甲")); assertTrue(answer.contains("机密乙")); assertTrue(answer.contains("结果未知"));
  conversations.complete(owner.getId(),conversation,turn,answer,"[]","{}",internal);
  var beforeMove=(java.util.List<?>)p.displayInteraction(internal).get("results");
  for (int i=0;i<2;i++) {
   var card=(java.util.Map<?,?>)beforeMove.get(i+2);
   assertEquals(i==0 ? "机密甲" : "机密乙",card.get("documentTitle")); assertEquals(a.name,card.get("folderName"));
   assertEquals(i==0 ? "正文甲" : "正文乙",choices.preview(owner,conversation,turn,String.valueOf(card.get("openToken"))).get("content"));
  }
  jdbc.update("UPDATE wiki_folder SET name=? WHERE id=?", "已改名",Long.valueOf(b.folderId));
  jdbc.update("UPDATE document_wiki SET folderId=? WHERE id=?",Long.valueOf(b.folderId),Long.valueOf(first.documentId));
  java.util.Map<String,Object> shown=conversations.messages(owner.getId(),conversation).stream().filter(r->"assistant".equals(r.get("role"))).findFirst().orElseThrow();
  String history=String.valueOf(shown); assertTrue(history.contains("已改名")); assertTrue(history.contains("机密甲")); assertFalse(history.contains(first.documentId)); assertFalse(history.contains("请选择写入空间"));
  java.util.Map<?,?> group=(java.util.Map<?,?>)shown.get("interaction"); java.util.List<?> results=(java.util.List<?>)group.get("results");
  java.util.Map<?,?> firstCard=(java.util.Map<?,?>)results.get(2); String token=String.valueOf(firstCard.get("openToken"));
  java.util.Map<?,?> secondCard=(java.util.Map<?,?>)results.get(3);
  assertEquals("机密甲",firstCard.get("documentTitle")); assertEquals("已改名",firstCard.get("folderName"));
  assertEquals("机密乙",secondCard.get("documentTitle")); assertEquals(a.name,secondCard.get("folderName"));
  assertEquals("正文甲",choices.preview(owner,conversation,turn,token).get("content"));
  assertEquals("正文乙",choices.preview(owner,conversation,turn,String.valueOf(secondCard.get("openToken"))).get("content"));
  jdbc.update("UPDATE document_wiki SET folderId=NULL WHERE id=?",Long.valueOf(first.documentId));
  assertEquals("", choices.resolveDocuments(owner,java.util.List.of(first.documentId)).get(first.documentId).get("folderName"));
  jdbc.update("UPDATE wiki_folder SET isDelete=1 WHERE id=?",Long.valueOf(a.folderId));
  java.util.Map<String,String> missingPath=choices.resolveDocuments(owner,java.util.List.of(second.documentId)).get(second.documentId);
  assertEquals("路径不可用",missingPath.get("folderName")); assertEquals("false",missingPath.get("validPath"));
  jdbc.update("UPDATE document_wiki SET isDelete=1 WHERE id=?",Long.valueOf(first.documentId));
  assertThrows(IllegalArgumentException.class,()->choices.preview(owner,conversation,turn,token));
  assertFalse(String.valueOf(conversations.messages(owner.getId(),conversation)).contains("机密甲"), String.valueOf(conversations.messages(owner.getId(),conversation)));
  jdbc.update("UPDATE wiki_space SET isDelete=1 WHERE id=?",Long.valueOf(space));
  String lost=String.valueOf(conversations.messages(owner.getId(),conversation));
  assertFalse(lost.contains("机密乙")); assertFalse(lost.contains("正文乙")); assertFalse(lost.contains("已改名"));
  assertTrue(choices.resolveDocuments(owner,java.util.List.of("9223372036854775807")).isEmpty());
  User stranger=new User(); stranger.setId(owner.getId()+1);
  assertThrows(IllegalArgumentException.class,()->choices.preview(stranger,conversation,turn,token));
 }

 @Test void controllerSsePersistsMultipleCardsAndUnknownWithoutOverwritingAnswer() throws Exception {
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","multi-stream-"+UUID.randomUUID(),"test-only",UserConstant.DEFAULT_ROLE);
  User owner=new User(); owner.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
  jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)","私人",owner.getId());
  String space=jdbc.queryForObject("SELECT LAST_INSERT_ID()",String.class);
  var a=creation.createFolder(owner.getId(),space,"甲目录"); var b=creation.createFolder(owner.getId(),space,"乙目录");
  var first=creation.createDocument(owner.getId(),space,a.folderId,"甲文档","甲内容");
  var second=creation.createDocument(owner.getId(),space,b.folderId,"乙文档","乙内容");
  var third=creation.createDocument(owner.getId(),space,a.folderId,"丙文档","丙内容");
  ObjectMapper json=new ObjectMapper();
  String frames="event: wiki_tool_event\ndata: {\"name\":\"create_wiki_folder\",\"result\":"+json.writeValueAsString(a)+"}\n\n"
    +"event: wiki_tool_event\ndata: {\"name\":\"create_wiki_folder\",\"result\":"+json.writeValueAsString(b)+"}\n\n"
    +"event: wiki_tool_event\ndata: {\"name\":\"create_wiki_document\",\"result\":"+json.writeValueAsString(first)+"}\n\n"
    +"event: wiki_tool_event\ndata: {\"name\":\"create_wiki_document\",\"result\":"+json.writeValueAsString(second)+"}\n\n"
    +"event: wiki_tool_event\ndata: {\"name\":\"create_wiki_document\",\"result\":"+json.writeValueAsString(third)+"}\n\n"
    +"event: wiki_tool_event\ndata: {\"name\":\"write_wiki_document\",\"result\":{\"status\":\"unknown\"}}\n\nevent: done\ndata: {}\n\n";
  com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/internal/agent/stream",exchange->{byte[] body=frames.getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,body.length); try(var out=exchange.getResponseBody()){out.write(body);}}); server.start();
  try {
   var proxy=new AgentPreviewProxy("http://127.0.0.1:"+server.getAddress().getPort(),"test-key");
   var controller=new AgentPreviewController(users,proxy,conversations,search,spaces,catalog,choices);
   var req=new org.springframework.mock.web.MockHttpServletRequest(); req.getSession().setAttribute(UserConstant.USER_LOGIN_STATE,owner);
   long conversation=conversations.create(owner.getId()); var ask=new AgentPreviewController.Ask("保存两个文档"); ask.conversationId=Long.toString(conversation);
   var out=new java.io.ByteArrayOutputStream(); controller.stream(ask,req).writeTo(out); String stream=out.toString(java.nio.charset.StandardCharsets.UTF_8);
   assertTrue(stream.contains("甲文档")); assertTrue(stream.contains("乙文档")); assertTrue(stream.contains("结果未知"));
   assertTrue(stream.contains("wiki_write_results")); assertFalse(stream.contains(first.documentId)); assertFalse(stream.contains(second.documentId));
   var assistant=conversations.messages(owner.getId(),conversation).stream().filter(r->"assistant".equals(r.get("role"))).findFirst().orElseThrow();
   String history=String.valueOf(assistant); assertTrue(history.contains("甲文档")); assertTrue(history.contains("乙文档")); assertTrue(history.contains("结果未知")); assertFalse(history.contains("请选择写入空间"));
   var results=(java.util.List<?>)((java.util.Map<?,?>)assistant.get("interaction")).get("results");
   assertEquals(6,results.size());
   var sseFrame=java.util.Arrays.stream(stream.split("\n\n")).filter(frame->frame.startsWith("event: wiki_result\n")).findFirst().orElseThrow();
   var live=json.readTree(sseFrame.substring(sseFrame.indexOf("data: ")+6)); var liveResults=live.path("results");
   assertEquals(6,liveResults.size());
   String[] titles={"甲文档","乙文档","丙文档"}, paths={a.name,b.name,a.name}, contents={"甲内容","乙内容","丙内容"};
   for(int i=0;i<3;i++) {
    var card=liveResults.get(i+2); assertEquals(titles[i],card.path("documentTitle").asText()); assertEquals(paths[i],card.path("folderName").asText());
    String token=card.path("openToken").asText(); assertFalse(token.isBlank());
    assertEquals(contents[i],choices.preview(owner,conversation,Long.parseLong(String.valueOf(assistant.get("turnId"))),token).get("content"));
    var historyCard=(java.util.Map<?,?>)results.get(i+2); assertEquals(token,historyCard.get("openToken"));
    assertEquals(titles[i],historyCard.get("documentTitle")); assertEquals(paths[i],historyCard.get("folderName"));
   }
   var cardA=(java.util.Map<?,?>)results.get(2); var cardB=(java.util.Map<?,?>)results.get(3);
   assertEquals("甲文档",cardA.get("documentTitle")); assertEquals(a.name,cardA.get("folderName"));
   assertEquals("乙文档",cardB.get("documentTitle")); assertEquals(b.name,cardB.get("folderName"));
   assertEquals("unknown",((java.util.Map<?,?>)results.get(5)).get("status"));
   assertEquals("甲内容",choices.preview(owner,conversation,Long.parseLong(String.valueOf(assistant.get("turnId"))),String.valueOf(cardA.get("openToken"))).get("content"));
   assertEquals("乙内容",choices.preview(owner,conversation,Long.parseLong(String.valueOf(assistant.get("turnId"))),String.valueOf(cardB.get("openToken"))).get("content"));
  } finally {server.stop(0);}
 }

 @Test void folderAndOrderedOutcomesPersistWithoutSensitiveDetails() throws Exception {
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","ordered-"+UUID.randomUUID(),"test-only",UserConstant.DEFAULT_ROLE);
  User owner=new User(); owner.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
  jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)","私人",owner.getId());
  String space=jdbc.queryForObject("SELECT LAST_INSERT_ID()",String.class);
  var folder=creation.createFolder(owner.getId(),space,"独立目录");
  ObjectMapper json=new ObjectMapper(); long conversation=conversations.create(owner.getId()); long turn=conversations.begin(owner.getId(),conversation,"目录","OFF","{}");
  AgentWikiChoiceProjection p=new AgentWikiChoiceProjection(choices,owner,conversation,turn);
  p.project("wiki_tool_event",json.readTree("{\"name\":\"create_wiki_folder\",\"result\":"+json.writeValueAsString(folder)+"}"));
  assertTrue(p.displayAnswer().contains("已创建文件夹")); assertFalse(p.displayAnswer().contains("文档尚未保存"));
  conversations.complete(owner.getId(),conversation,turn,p.displayAnswer(),"[]","{}",p.interactionJson());
  String only=String.valueOf(conversations.messages(owner.getId(),conversation));
  assertTrue(only.contains("已创建文件夹")); assertFalse(only.contains("文档未保存"));
  long next=conversations.begin(owner.getId(),conversation,"写入","OFF","{}");
  p=new AgentWikiChoiceProjection(choices,owner,conversation,next);
  p.project("wiki_tool_event",json.readTree("{\"name\":\"create_wiki_folder\",\"result\":"+json.writeValueAsString(folder)+"}"));
  p.project("wiki_tool_event",json.readTree("{\"name\":\"create_wiki_document\",\"result\":{\"saved\":false,\"error\":\"sensitive secret\"}}"));
  p.project("wiki_tool_event",json.readTree("{\"name\":\"write_wiki_document\",\"result\":{\"status\":\"blocked\"}}"));
  p.project("wiki_tool_event",json.readTree("{\"name\":\"create_wiki_folder\",\"result\":{\"status\":\"unknown\"}}"));
  var display=p.displayInteraction(p.interactionJson()); var rows=(java.util.List<?>)display.get("results");
  assertEquals(4,rows.size());
  assertEquals("wiki_folder_result",((java.util.Map<?,?>)rows.get(0)).get("kind"));
  assertEquals("failed",((java.util.Map<?,?>)rows.get(1)).get("status"));
  assertEquals("blocked",((java.util.Map<?,?>)rows.get(2)).get("status"));
  assertEquals("unknown",((java.util.Map<?,?>)rows.get(3)).get("status"));
  String answer=p.displayAnswer(); assertFalse(answer.contains("sensitive secret")); assertFalse(answer.contains(folder.folderId));
  conversations.complete(owner.getId(),conversation,next,answer,"[]","{}",p.interactionJson());
  var assistant=conversations.messages(owner.getId(),conversation).stream().filter(r->Long.toString(next).equals(String.valueOf(r.get("turnId"))) && "assistant".equals(r.get("role"))).findFirst().orElseThrow();
  assertEquals(display,assistant.get("interaction")); assertEquals(answer,assistant.get("content"));
 }

 @Test void folderOnlyAndFailuresStreamAndReloadInCallOrder() throws Exception {
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","ordered-stream-"+UUID.randomUUID(),"test-only",UserConstant.DEFAULT_ROLE);
  User owner=new User(); owner.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
  jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)","私人",owner.getId());
  String space=jdbc.queryForObject("SELECT LAST_INSERT_ID()",String.class);
  var folder=creation.createFolder(owner.getId(),space,"流目录"); ObjectMapper json=new ObjectMapper();
  String folderEvent="event: wiki_tool_event\ndata: {\"name\":\"create_wiki_folder\",\"result\":"+json.writeValueAsString(folder)+"}\n\n";
  String failureEvents="event: wiki_tool_event\ndata: {\"name\":\"create_wiki_document\",\"result\":{\"saved\":false,\"error\":\"secret phrase\"}}\n\n"
   +"event: wiki_tool_event\ndata: {\"name\":\"write_wiki_document\",\"result\":{\"status\":\"blocked\"}}\n\n"
   +"event: wiki_tool_event\ndata: {\"name\":\"create_wiki_folder\",\"result\":{\"status\":\"unknown\"}}\n\n";
  var response=new java.util.concurrent.atomic.AtomicReference<>(folderEvent+"event: done\ndata: {}\n\n");
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/internal/agent/stream",exchange->{byte[] body=response.get().getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,body.length); try(var out=exchange.getResponseBody()){out.write(body);}}); server.start();
  try {
   var proxy=new AgentPreviewProxy("http://127.0.0.1:"+server.getAddress().getPort(),"test-key");
   var controller=new AgentPreviewController(users,proxy,conversations,search,spaces,catalog,choices);
   var req=new org.springframework.mock.web.MockHttpServletRequest(); req.getSession().setAttribute(UserConstant.USER_LOGIN_STATE,owner);
   for (boolean multiple : new boolean[]{false,true}) {
    if (multiple) response.set(folderEvent+failureEvents+"event: done\ndata: {}\n\n");
    long conversation=conversations.create(owner.getId()); var ask=new AgentPreviewController.Ask("操作"); ask.conversationId=Long.toString(conversation);
    var out=new java.io.ByteArrayOutputStream(); controller.stream(ask,req).writeTo(out); String stream=out.toString(java.nio.charset.StandardCharsets.UTF_8);
    var assistant=conversations.messages(owner.getId(),conversation).stream().filter(r->"assistant".equals(r.get("role"))).findFirst().orElseThrow();
    String history=String.valueOf(assistant); assertTrue(stream.contains("已创建文件夹")); assertTrue(history.contains("已创建文件夹"));
    assertFalse(stream.contains("secret phrase")); assertFalse(history.contains("secret phrase"));
    if (!multiple) {assertFalse(stream.contains("文档未保存")); assertFalse(history.contains("文档未保存"));}
    else {
     var rows=(java.util.List<?>)((java.util.Map<?,?>)assistant.get("interaction")).get("results");
     assertEquals(4,rows.size()); assertEquals("failed",((java.util.Map<?,?>)rows.get(1)).get("status"));
     assertEquals("blocked",((java.util.Map<?,?>)rows.get(2)).get("status")); assertEquals("unknown",((java.util.Map<?,?>)rows.get(3)).get("status"));
     assertTrue(stream.indexOf("操作失败")<stream.indexOf("操作被阻止")); assertTrue(stream.indexOf("操作被阻止")<stream.indexOf("结果未知"));
     assertFalse(stream.contains("重试")); assertFalse(history.contains("重试"));
    }
   }
  } finally { server.stop(0); }
 }

 @Test void wikiReadOnlyFinalAnswerIsStreamedAndSavedForSuccessAndError() throws Exception {
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","read-stream-"+UUID.randomUUID(),"test-only",UserConstant.DEFAULT_ROLE);
  User owner=new User(); owner.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
  var reply=new java.util.concurrent.atomic.AtomicReference<>("");
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/internal/agent/stream",exchange->{byte[] body=reply.get().getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,body.length); try(var out=exchange.getResponseBody()){out.write(body);}}); server.start();
  try {
   var proxy=new AgentPreviewProxy("http://127.0.0.1:"+server.getAddress().getPort(),"test-key");
   var controller=new AgentPreviewController(users,proxy,conversations,search,spaces,catalog,choices);
   var req=new org.springframework.mock.web.MockHttpServletRequest(); req.getSession().setAttribute(UserConstant.USER_LOGIN_STATE,owner);
   for(boolean failed:new boolean[]{false,true}) {
    String answer=failed?"Read failed. Please check access.":"查询完成，可选择保存位置。";
    String result=failed?"{\"code\":40101,\"message\":\"request rejected\"}":"{\"spaces\":[{\"id\":\"123456789012345678\",\"name\":\"legacy secret\",\"type\":\"personal\"}]}";
    reply.set("event: delta\ndata: "+new ObjectMapper().writeValueAsString(java.util.Map.of("content",answer))+"\n\n"
      +"event: wiki_tool_event\ndata: {\"name\":\"list_writable_wiki_locations\",\"result\":"+result+"}\n\nevent: done\ndata: {}\n\n");
    long conversation=conversations.create(owner.getId()); var ask=new AgentPreviewController.Ask("查询"); ask.conversationId=Long.toString(conversation);
    var out=new java.io.ByteArrayOutputStream(); controller.stream(ask,req).writeTo(out); String stream=out.toString(java.nio.charset.StandardCharsets.UTF_8);
    var assistant=conversations.messages(owner.getId(),conversation).stream().filter(r->"assistant".equals(r.get("role"))).findFirst().orElseThrow();
    assertTrue(stream.contains(answer)); assertEquals(answer,assistant.get("content"));
    assertFalse(stream.contains("123456789012345678")); assertFalse(stream.contains("legacy secret")); assertFalse(stream.contains("request rejected"));
    assertFalse(String.valueOf(assistant).contains("legacy secret"));
   }
  } finally {server.stop(0);}
 }

 @Test void sharedFixedLocationSkillKeepsVersionAndChecksCurrentUserWikiAccess() throws Exception {
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","skill-author-"+UUID.randomUUID(),"test-only",UserConstant.DEFAULT_ROLE);
  long author=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","skill-user-"+UUID.randomUUID(),"test-only",UserConstant.DEFAULT_ROLE);
  long user=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
  jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(1,?,?,0)","共享空间",author);
  String space=jdbc.queryForObject("SELECT LAST_INSERT_ID()",String.class);
  jdbc.update("INSERT INTO wiki_space_user(spaceId,userId,spaceRole,isDelete) VALUES(?,?,?,0)",Long.valueOf(space),author,"editor");
  jdbc.update("INSERT INTO wiki_space_user(spaceId,userId,spaceRole,isDelete) VALUES(?,?,?,0)",Long.valueOf(space),user,"editor");
  var folder=creation.createFolder(author,space,"Skill目录");
  String definition="{\"name\":\"Shared\",\"summary\":\"Save\",\"when_to_use\":\"Save\",\"when_not_to_use\":\"Other\",\"parameters\":[],\"steps\":[{\"type\":\"tool\",\"tool\":\"create_wiki_document\",\"args\":{\"spaceId\":\""+space+"\",\"folderId\":\""+folder.folderId+"\",\"content\":\"正文\"}}],\"expected_results\":[\"Saved\"],\"failure_policy\":\"stop\",\"examples\":[{\"input\":{},\"expected\":\"Saved\"}]}";
  String skill=library.createSkill(author,definition); library.shareSkill(author,skill,user);
  var shared=library.enabledSkillVersion(user,skill,1); assertEquals(1,shared.version); assertEquals(definition,shared.definitionJson);
  var written=creation.createDocument(user,space,folder.folderId,"共享文档","正文");
  assertEquals(user,jdbc.queryForObject("SELECT userId FROM document_wiki WHERE id=?",Long.class,Long.valueOf(written.documentId)));
  long before=jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE spaceId=? AND isDelete=0",Long.class,Long.valueOf(space));
  jdbc.update("UPDATE wiki_space_user SET isDelete=1 WHERE spaceId=? AND userId=?",Long.valueOf(space),user);
  assertEquals(definition,library.enabledSkillVersion(user,skill,1).definitionJson);
  var denied=assertThrows(com.et.cloud.exception.BusinessException.class,()->creation.createDocument(user,space,folder.folderId,"失权文档","正文"));
  assertEquals(com.et.cloud.exception.ErrorCode.NO_AUTH_ERROR.getCode(),denied.getCode());
  assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE spaceId=? AND isDelete=0",Long.class,Long.valueOf(space)));
  assertEquals(definition,library.skillVersion(author,skill,1).definitionJson); assertEquals(1,library.skillVersion(author,skill,1).version);
 }

 @Test void freshMembershipAndInvalidAncestorChainsAreRejected() throws Exception {
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","multi-member-"+UUID.randomUUID(),"test-only",UserConstant.DEFAULT_ROLE);
  User owner=new User(); owner.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","multi-stranger-"+UUID.randomUUID(),"test-only",UserConstant.DEFAULT_ROLE);
  User stranger=new User(); stranger.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
  jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(1,?,?,0)","团队私密",owner.getId());
  String space=jdbc.queryForObject("SELECT LAST_INSERT_ID()",String.class);
  jdbc.update("INSERT INTO wiki_space_user(spaceId,userId,spaceRole,isDelete) VALUES(?,?,?,0)",Long.valueOf(space),owner.getId(),"editor");
  var folder=creation.createFolder(owner.getId(),space,"路径机密");
  var doc=creation.createDocument(owner.getId(),space,folder.folderId,"标题机密","正文机密");
  assertTrue(choices.resolveDocuments(stranger,java.util.List.of(doc.documentId)).isEmpty());
  assertEquals("true",choices.resolveDocuments(owner,java.util.List.of(doc.documentId)).get(doc.documentId).get("validPath"));
  jdbc.update("UPDATE wiki_folder SET parentId=id WHERE id=?",Long.valueOf(folder.folderId));
  assertEquals("路径不可用",choices.resolveDocuments(owner,java.util.List.of(doc.documentId)).get(doc.documentId).get("folderName"));
  jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)","另一空间",owner.getId());
  String other=jdbc.queryForObject("SELECT LAST_INSERT_ID()",String.class);
  var otherFolder=creation.createFolder(owner.getId(),other,"不可泄露的上级");
  jdbc.update("UPDATE wiki_folder SET parentId=? WHERE id=?",Long.valueOf(otherFolder.folderId),Long.valueOf(folder.folderId));
  String invalid=String.valueOf(choices.resolveDocuments(owner,java.util.List.of(doc.documentId)));
  assertTrue(invalid.contains("路径不可用")); assertFalse(invalid.contains("不可泄露的上级"));
  jdbc.update("UPDATE wiki_space_user SET isDelete=1 WHERE spaceId=? AND userId=?",Long.valueOf(space),owner.getId());
  assertTrue(choices.resolveDocuments(owner,java.util.List.of(doc.documentId)).isEmpty());
 }

 @Test void documentLookupsAreBatchedAndDepthIsBounded() throws Exception {
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","multi-batch-"+UUID.randomUUID(),"test-only",UserConstant.DEFAULT_ROLE);
  User owner=new User(); owner.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
  jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)","私人",owner.getId());
  String space=jdbc.queryForObject("SELECT LAST_INSERT_ID()",String.class);
  var folder=creation.createFolder(owner.getId(),space,"批量目录");
  java.util.List<String> ids=new java.util.ArrayList<>();
  for(int i=0;i<51;i++) ids.add(creation.createDocument(owner.getId(),space,folder.folderId,"文档"+i,"正文").documentId);
  long before=jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Com_select'",(rs,n)->Long.parseLong(rs.getString(2)));
  assertEquals(51,choices.resolveDocuments(owner,ids).size());
  long after=jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Com_select'",(rs,n)->Long.parseLong(rs.getString(2)));
  assertTrue(after-before < 12, "51 documents should require bounded batches, not one query per document: "+(after-before));
  java.util.List<String> historyStates=new java.util.ArrayList<>(); java.util.List<Boolean> used=new java.util.ArrayList<>();
  for(String id:ids) { historyStates.add("{\"kind\":\"wiki_write_result\",\"turnId\":\"123\",\"documentId\":\""+id+"\",\"openToken\":\"opaque\"}"); used.add(false); }
  before=jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Com_select'",(rs,n)->Long.parseLong(rs.getString(2)));
  assertEquals(51,choices.currentDisplays(owner,historyStates,used).size());
  after=jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Com_select'",(rs,n)->Long.parseLong(rs.getString(2)));
  assertTrue(after-before < 12, "history must batch across messages: "+(after-before));
  Long parent=Long.valueOf(folder.folderId);
  for(int i=0;i<255;i++) {
   jdbc.update("INSERT INTO wiki_folder(spaceId,parentId,name,isDelete) VALUES(?,?,?,0)",Long.valueOf(space),parent,"层"+i);
   parent=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
  }
  jdbc.update("UPDATE document_wiki SET folderId=? WHERE id=?",parent,Long.valueOf(ids.get(0)));
  assertEquals("true",choices.resolveDocuments(owner,java.util.List.of(ids.get(0))).get(ids.get(0)).get("validPath"));
  jdbc.update("INSERT INTO wiki_folder(spaceId,parentId,name,isDelete) VALUES(?,?,?,0)",Long.valueOf(space),parent,"超过上限");
  parent=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
  jdbc.update("UPDATE document_wiki SET folderId=? WHERE id=?",parent,Long.valueOf(ids.get(0)));
  var location=choices.resolveDocuments(owner,java.util.List.of(ids.get(0))).get(ids.get(0));
  assertEquals("false",location.get("validPath")); assertEquals("路径不可用",location.get("folderName"));
 }

 @Test void unknownOnlyIsPersistedWithoutGuessingExistingDocument() throws Exception {
  jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","multi-unknown-"+UUID.randomUUID(),"test-only",UserConstant.DEFAULT_ROLE);
  User owner=new User(); owner.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
  jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)","私人",owner.getId());
  String space=jdbc.queryForObject("SELECT LAST_INSERT_ID()",String.class);
  var folder=creation.createFolder(owner.getId(),space,"目标");
  var existing=creation.createDocument(owner.getId(),space,folder.folderId,"看似匹配","旧正文");
  long conversation=conversations.create(owner.getId()); long turn=conversations.begin(owner.getId(),conversation,"写入","OFF","{}");
  AgentWikiChoiceProjection p=new AgentWikiChoiceProjection(choices,owner,conversation,turn);
  p.project("wiki_tool_event",new ObjectMapper().readTree("{\"name\":\"write_wiki_document\",\"result\":{\"status\":\"unknown\"}}"));
  conversations.complete(owner.getId(),conversation,turn,p.displayAnswer(),"[]","{}",p.interactionJson());
  String history=String.valueOf(conversations.messages(owner.getId(),conversation));
  assertTrue(history.contains("结果未知")); assertFalse(history.contains("看似匹配")); assertFalse(history.contains(existing.documentId));
  assertFalse(history.contains("旧正文")); assertFalse(history.contains("文档未保存"));
 }
}

package com.et.cloud.controller;
import com.et.cloud.model.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
public class Round3ExtraProbe {
 static ObjectMapper j=new ObjectMapper();
 static void event(AgentWikiChoiceProjection p,String x)throws Exception{p.project("wiki_tool_event",j.readTree(x));}
 public static void main(String[]a)throws Exception{
 User u=new User();u.setId(7L); AgentWikiChoiceService s=mock(AgentWikiChoiceService.class); when(s.displayLabelFor(u,"55")).thenReturn("Personal");
 AgentWikiChoiceProjection p=new AgentWikiChoiceProjection(s,u,11,12);
 event(p,"{\"name\":\"create_wiki_document\",\"result\":{\"spaceId\":\"55\",\"folderId\":\"123\",\"folderName\":\"Folder A\",\"documentId\":\"456\",\"title\":\"Document A\"}}");
 event(p,"{\"name\":\"create_wiki_folder\",\"result\":{\"spaceId\":\"55\",\"folderId\":\"124\",\"name\":\"Folder B\"}}");
 event(p,"{\"name\":\"write_wiki_document\",\"result\":{\"status\":\"unknown\"}}");
 System.out.println("CASE1 answer="+p.displayAnswer()); System.out.println("CASE1 card="+AgentWikiChoiceService.safeDisplay(p.interactionJson(),false));
 AgentWikiChoiceProjection q=new AgentWikiChoiceProjection(s,u,11,12);
 event(q,"{\"name\":\"create_wiki_document\",\"result\":{\"spaceId\":\"55\",\"folderId\":\"123\",\"folderName\":\"Folder A\",\"documentId\":\"456\",\"title\":\"Document A\"}}");
 event(q,"{\"name\":\"create_wiki_document\",\"result\":{\"spaceId\":\"55\",\"folderId\":\"123\",\"folderName\":\"Folder A\",\"documentId\":\"457\",\"title\":\"Document B\"}}");
 System.out.println("CASE2 answer="+q.displayAnswer()); System.out.println("CASE2 card="+AgentWikiChoiceService.safeDisplay(q.interactionJson(),false));
 }
}
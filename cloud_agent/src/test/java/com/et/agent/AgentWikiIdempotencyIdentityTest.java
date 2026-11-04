package com.et.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentWikiIdempotencyIdentityTest {
    @Test void identitiesAreServerGeneratedPerInvocationAndStableForControlledResend() throws Exception {
        List<JsonNode> requests=Collections.synchronizedList(new ArrayList<>());
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/internal/agent/wiki-documents/create",x->{
            requests.add(new ObjectMapper().readTree(x.getRequestBody()));
            byte[] response="{\"code\":0,\"data\":{\"documentId\":\"9\"}}".getBytes(StandardCharsets.UTF_8);
            x.sendResponseHeaders(200,response.length);try(java.io.OutputStream out=x.getResponseBody()){out.write(response);}
        });server.start();
        try{
            AgentLibraryHttpGateway gateway=new AgentLibraryHttpGateway("http://127.0.0.1:"+server.getAddress().getPort(),"test-only");
            gateway.createWikiDocument("7","11","12","title","synthetic content");
            gateway.createWikiDocument("7","11","12","title","synthetic content");
            assertTrue(requests.get(0).hasNonNull("operationId"),"server-generated operation ID must be present");
            String first=requests.get(0).path("operationId").asText(),second=requests.get(1).path("operationId").asText();
            assertNotEquals(first,second); assertEquals(first,UUID.fromString(first).toString());assertEquals(second,UUID.fromString(second).toString());
            Method factory=AgentLibraryHttpGateway.class.getDeclaredMethod("newWikiCreateOperation",String.class,String.class,String.class,String.class,String.class);factory.setAccessible(true);
            Object operation=factory.invoke(gateway,"7","11","12","title","synthetic content");Method send=operation.getClass().getDeclaredMethod("execute");send.setAccessible(true);send.invoke(operation);send.invoke(operation);
            assertEquals(requests.get(2).path("operationId").asText(),requests.get(3).path("operationId").asText());
            assertNotEquals(first,requests.get(2).path("operationId").asText());
            assertThrows(IllegalArgumentException.class,()->AgentToolRegistry.bind("7",gateway).get("create_wiki_document").execute("{\"spaceId\":\"11\",\"folderId\":\"12\",\"content\":\"x\",\"operationId\":\"model-supplied\"}"));
            assertEquals(4,requests.size());
        }finally{server.stop(0);}
    }
}

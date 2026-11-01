package com.et.agent;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentUrlImportToolTest {
    @Test void modelCannotSupplyUserAndSelectedSpaceIsEnforced() {
        AgentLibraryGateway gateway=mock(AgentLibraryGateway.class);
        when(gateway.importWikiUrls(eq("7"),eq("11"),isNull(),anyList())).thenReturn("{\"items\":[]}");
        Map<String,AgentLoop.Tool> tools=AgentToolRegistry.bind("7",gateway,"11");
        assertThrows(IllegalArgumentException.class,()->tools.get("import_wiki_urls").execute("{\"spaceId\":\"11\",\"urls\":[],\"userId\":\"8\"}"));
        assertThrows(IllegalArgumentException.class,()->tools.get("import_wiki_urls").execute("{\"spaceId\":\"12\",\"urls\":[]}"));
        tools.get("import_wiki_urls").execute("{\"spaceId\":\"11\",\"folderId\":null,\"urls\":[\"https://example.org\"]}");
        verify(gateway).importWikiUrls("7","11",null,List.of("https://example.org"));
    }
    @Test void unknownImportBlocksSubsequentWrites() {
        AtomicInteger writes=new AtomicInteger(), turns=new AtomicInteger();
        AgentLoop.ToolAwareModel model=(history,names,delta,cancelled)-> {
            int turn=turns.getAndIncrement();
            if (turn==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1","import_wiki_urls","{}")),null);
            if (turn==1) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("2","write_file","{}")),null);
            assertTrue(history.get(history.size()-1).content.contains("blocked"));
            return new AgentLoop.Turn(List.of(),null);
        };
        var tools=Map.<String,AgentLoop.Tool>of("import_wiki_urls",x->{writes.incrementAndGet(); return "{\"status\":\"unknown\",\"items\":[]}";},
                "write_file",x->{writes.incrementAndGet();return "{}";});
        new AgentLoop(model,Map.of(),4,10000,10000).run("保存",List.of(),null,null,tools,x->{},()->false);
        assertEquals(1,writes.get());
    }
    @Test void ordinarySummaryDoesNotWrite() {
        AtomicInteger writes=new AtomicInteger();
        AgentLoop.ToolAwareModel model=(h,n,d,c)->new AgentLoop.Turn(List.of(),null);
        new AgentLoop(model,Map.of(),3,10000,10000).run("总结网页",List.of(),null,null,
                Map.of("import_wiki_urls",x->{writes.incrementAndGet();return "{}";}),x->{},()->false);
        assertEquals(0,writes.get());
    }
    @Test void aliasesProtectIdentifiersWhileGatewayReceivesSelectedTarget() throws Exception {
        AgentLibraryGateway gateway=mock(AgentLibraryGateway.class);
        when(gateway.listWritableWikiLocations(eq("7"),isNull(),isNull(),isNull())).thenReturn("{\"spaces\":[{\"id\":\"11\"}]}");
        when(gateway.importWikiUrls(eq("7"),eq("11"),isNull(),anyList())).thenReturn("{\"items\":[{\"input\":\"https://example.org\",\"status\":\"success\",\"spaceId\":\"11\",\"documentId\":\"99999999999999\",\"title\":\"Example\"}]}");
        AtomicInteger turn=new AtomicInteger();
        AgentLoop.ToolAwareModel model=(history,names,delta,cancelled)->{
            int n=turn.getAndIncrement();
            if(n==0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1","list_writable_wiki_locations","{}")),null);
            if(n==1) {
                try {
                    String alias=new com.fasterxml.jackson.databind.ObjectMapper().readTree(history.get(history.size()-1).content).path("spaces").get(0).path("id").asText();
                    assertTrue(alias.startsWith("wiki_"));
                    return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("2","import_wiki_urls","{\"spaceId\":\""+alias+"\",\"urls\":[\"https://example.org\"]}")),null);
                } catch(java.io.IOException e) {throw new AssertionError(e);}
            }
            assertFalse(history.get(history.size()-1).content.contains("99999999999999"));
            return new AgentLoop.Turn(List.of(),null);
        };
        AgentLoop.Result result=new AgentLoop(model,Map.of(),4,10000,10000).run("保存网页",List.of(),null,null,
                AgentToolRegistry.bind("7",gateway,"11"),x->{},()->false);
        verify(gateway).importWikiUrls("7","11",null,List.of("https://example.org"));
        assertTrue(result.wikiEvents.stream().anyMatch(e->e.name.equals("import_wiki_urls") && e.result.contains("99999999999999")));
    }
    @Test void oversizedToolResultStillKeepsWriteEvent() {
        AgentLoop.ToolAwareModel model=(h,n,d,c)->new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1","import_wiki_urls","{}")),null);
        String raw="{\"items\":[{\"status\":\"success\",\"documentId\":\"31\"}]}";
        AgentLoop.Result result=new AgentLoop(model,Map.of(),3,10000,1).run("保存",List.of(),null,null,
                Map.of("import_wiki_urls",x->raw),x->{},()->false);
        assertTrue(result.wikiEvents.stream().anyMatch(e->e.name.equals("import_wiki_urls") && e.result.equals(raw)));
    }
    @Test void interruptedHttpWriteIsUnknownAndNotRetried() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        AtomicInteger requests=new AtomicInteger();
        server.createContext("/api/internal/agent/wiki-url-imports",exchange->{requests.incrementAndGet();exchange.close();});
        server.start();
        try {
            AgentLibraryHttpGateway gateway=new AgentLibraryHttpGateway("http://127.0.0.1:"+server.getAddress().getPort(),"secret",java.time.Duration.ofSeconds(2));
            AgentLibraryHttpGateway.CloudFailure error=assertThrows(AgentLibraryHttpGateway.CloudFailure.class,()->gateway.importWikiUrls("7","11",null,List.of("https://example.org")));
            assertTrue(error.unknown); assertEquals(1,requests.get());
        } finally {server.stop(0);}
    }
}

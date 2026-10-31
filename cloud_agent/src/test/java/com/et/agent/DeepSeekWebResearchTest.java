package com.et.agent;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeepSeekWebResearchTest {
    static final ObjectMapper JSON = new ObjectMapper();
    HttpServer server; String base; AtomicInteger calls; List<String> requests; List<String> auth; int status; String response; int delay;
    @BeforeEach void start() throws Exception {
        calls = new AtomicInteger(); requests = new ArrayList<>(); auth = new ArrayList<>(); status = 200; delay = 0;
        response = "{\"results\":[{\"title\":\"标题\",\"url\":\"https://example.com/x\",\"snippet\":\"短摘要\",\"summary\":\"长摘要\"}]}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tools/", ex -> {
            calls.incrementAndGet(); requests.add(ex.getRequestURI().getPath() + " " + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.add(ex.getRequestHeaders().getFirst("Authorization"));
            if (delay > 0) try { Thread.sleep(delay); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            try { ex.sendResponseHeaders(status, bytes.length); ex.getResponseBody().write(bytes); } finally { ex.close(); }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; }));
        server.start(); base = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void stop() { server.stop(0); }
    Map<String, AgentLoop.Tool> tools() { return new DeepSeekWebResearch("secret-key", base, 1000).bind(); }
    JsonNode parse(String s) throws Exception { return JSON.readTree(s); }
    @Test void searchAndFetchContractAndBounds() throws Exception {
        Map<String, AgentLoop.Tool> t = tools();
        JsonNode search = parse(t.get("web_search").execute("{\"query\":\"中文搜索\"}"));
        assertEquals("标题", search.path("results").get(0).path("title").asText());
        assertEquals("长摘要", search.path("results").get(0).path("summary").asText());
        assertFalse(search.path("results").get(0).path("content_available").asBoolean());
        assertTrue(requests.get(0).contains("中文搜索"));
        assertTrue(requests.get(0).contains("\"count\":5")); assertTrue(requests.get(0).contains("\"need_content\":false"));
        response = "{\"title\":\"网页标题\",\"content\":\"# 正文\"}";
        JsonNode fetch = parse(t.get("web_fetch").execute("{\"url\":\"https://example.com/path\"}"));
        assertEquals("网页标题", fetch.path("title").asText()); assertEquals("# 正文", fetch.path("content").asText());
        assertTrue(fetch.path("content_available").asBoolean()); assertTrue(requests.get(1).contains("https://example.com/path"));
        assertEquals(List.of("Bearer secret-key", "Bearer secret-key"), auth);
        response = "{\"results\":[]}"; assertEquals("no_results", parse(t.get("web_search").execute("{\"query\":\"x\"}")).path("status").asText());
        int before = calls.get();
        for (String bad : List.of("ftp://example.com", "https://u:p@example.com/", "https:///missing", "https://example.com/" + "a".repeat(8193)))
            assertEquals("invalid_argument", parse(t.get("web_fetch").execute(JSON.createObjectNode().put("url", bad).toString())).path("error").asText());
        assertEquals("invalid_argument", parse(t.get("web_search").execute("{\"query\":\"   \"}")).path("error").asText());
        assertEquals(before, calls.get());
        response = JSON.createObjectNode().put("title", "secret-key").put("content", "secret-key" + "中".repeat(10000)).toString();
        fetch = parse(t.get("web_fetch").execute("{\"url\":\"https://example.com/\"}")); assertTrue(fetch.path("content").asText().length() <= 5000); assertFalse(fetch.toString().contains("secret-key"));
    }
    @Test void errorsRetriesBudgetsAndIsolation() throws Exception {
        for (int code : new int[]{401,402,403,404,429,500}) {
            status = code; response = "secret-key"; int before = calls.get();
            String expected = code == 401 ? "authentication" : code == 402 ? "insufficient_balance" : code == 403 ? "forbidden" : code == 404 ? "not_found" : code == 429 ? "rate_limited" : "upstream_failure";
            String output = tools().get("web_fetch").execute("{\"url\":\"https://example.com\"}");
            assertEquals(expected, parse(output).path("error").asText()); assertFalse(output.contains("secret-key"));
            assertEquals(code == 429 || code == 500 ? 2 : 1, calls.get() - before);
        }
        status = 500; int before = calls.get(); Map<String,AgentLoop.Tool> t = tools();
        t.get("web_search").execute("{\"query\":\"x\"}"); t.get("web_search").execute("{\"query\":\"x\"}");
        assertEquals("budget_exhausted", parse(t.get("web_search").execute("{\"query\":\"x\"}")).path("error").asText());
        assertEquals(3, calls.get() - before);
        t.get("web_fetch").execute("{\"url\":\"https://example.com\"}"); assertEquals(5, calls.get() - before);
        tools().get("web_search").execute("{\"query\":\"x\"}"); assertEquals(7, calls.get() - before);
        status = 200; delay = 200; before = calls.get();
        String timeout = new DeepSeekWebResearch("secret-key", base, 30).bind().get("web_search").execute("{\"query\":\"x\"}");
        assertEquals("timeout", parse(timeout).path("error").asText()); assertEquals(2, calls.get() - before);
    }
    @Test void failedFetchAndEarlierSummaryReachModel() {
        AtomicInteger turn = new AtomicInteger();
        AgentLoop.ToolAwareModel model = (history, names, delta, cancelled) -> {
            int n = turn.getAndIncrement();
            if (n == 0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("s", "web_search", "{\"query\":\"中文\"}")), null);
            if (n == 1) { status = 404; return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("f", "web_fetch", "{\"url\":\"https://example.com/x\"}")), null); }
            assertTrue(history.stream().anyMatch(m -> "web_search".equals(m.name) && m.content.contains("长摘要")));
            assertTrue(history.stream().anyMatch(m -> "web_fetch".equals(m.name) && m.content.contains("not_found")));
            return new AgentLoop.Turn(Collections.emptyList(), null);
        };
        new AgentLoop(model, Collections.emptyMap(), 4, 10000, 16000).withWeb(new DeepSeekWebResearch("secret-key", base, 1000)).run("q", x -> {}, () -> false);
        assertEquals(3, turn.get());
    }
    @Test void modelSeesSourcesContentStatusAndInstructionsAndDisabledPreservesTools() throws Exception {
        AtomicInteger turn = new AtomicInteger();
        AgentLoop.ToolAwareModel model = (history, names, delta, cancelled) -> {
            assertTrue(names.containsAll(Set.of("web_search", "web_fetch", "get_skill")));
            assertTrue(history.get(0).content.contains("untrusted")); assertTrue(history.get(0).content.contains("only on search summaries")); assertTrue(history.get(0).content.contains("do not guess"));
            int n = turn.getAndIncrement();
            if (n == 0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1", "web_search", "{\"query\":\"中文\"}")), null);
            if (n == 1) { assertTrue(history.get(history.size()-1).content.contains("长摘要")); assertTrue(history.get(history.size()-1).content.contains("https://example.com/x")); response="{\"title\":\"标题\",\"content\":\"# 正文\"}"; return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("2", "web_fetch", "{\"url\":\"https://example.com/x\"}")), null); }
            assertTrue(history.get(history.size()-1).content.contains("正文")); assertTrue(history.get(history.size()-1).content.contains("content_available"));
            return new AgentLoop.Turn(Collections.emptyList(), null);
        };
        AgentLibraryGateway gateway = mock(AgentLibraryGateway.class); when(gateway.getSkill("7", "x", 1)).thenReturn("{}");
        Map<String,AgentLoop.Tool> library = AgentToolRegistry.bind("7", gateway);
        new AgentLoop(model, library, 4, 10000, 16000).withWeb(new DeepSeekWebResearch("secret-key", base, 1000)).run("问题", x -> {}, () -> false);
        assertEquals(3, turn.get());
        AtomicInteger disabledTurn = new AtomicInteger();
        AgentLoop.ToolAwareModel disabled = (h, names, d, c) -> { assertFalse(names.contains("web_search")); assertFalse(names.contains("web_fetch")); assertTrue(names.contains("get_skill"));
            if (disabledTurn.getAndIncrement() == 0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("3", "get_skill", "{\"id\":\"x\",\"version\":1}")), null);
            assertEquals("{}", h.get(h.size()-1).content); return new AgentLoop.Turn(Collections.emptyList(), null); };
        new AgentLoop(disabled, library, 2, 10000, 16000).withWeb(new DeepSeekWebResearch("")).run("q", x -> {}, () -> false);
        assertEquals("web_search", AgentToolRegistry.declaration("web_search").path("function").path("name").asText());
    }
}

package com.et.cloud.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Re-ranking is the optional stage that orders the candidate pool. Two
 * properties matter more than the scores themselves:
 * it must be trivially switchable off, and every failure must surface as a
 * typed exception so the search pipeline can fall back instead of failing.
 */
class RagRerankClientTest {

    private HttpServer server;
    private RagProperties properties;
    private String lastRequestBody;
    private String lastAuthHeader;
    private int responseStatus = 200;
    private String responseBody = "{\"output\":{\"results\":["
            + "{\"index\":2,\"relevance_score\":0.91},"
            + "{\"index\":0,\"relevance_score\":0.55},"
            + "{\"index\":1,\"relevance_score\":0.12}]}}";
    private final AtomicInteger requestCount = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rerank", exchange -> {
            requestCount.incrementAndGet();
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        properties = new RagProperties();
        properties.getRetrieval().getRerank().setBaseUrl(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/rerank");
        properties.getRetrieval().getRerank().setApiKey("test-key");
        properties.getRetrieval().getRerank().setModel("gte-rerank-v2");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private RagRerankClient client() {
        return new RagRerankClient(properties);
    }

    private static List<String> documents(int count) {
        List<String> docs = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            docs.add("文档" + i);
        }
        return docs;
    }

    // --------------------------------------------------------------- usability

    @Test
    void usableOnlyWhenEnabledAndKeyed() {
        assertTrue(client().isUsable());
        properties.getRetrieval().getRerank().setApiKey("");
        assertFalse(client().isUsable());
        properties.getRetrieval().getRerank().setApiKey("k");
        properties.getRetrieval().getRerank().setEnabled(false);
        assertFalse(client().isUsable());
    }

    @Test
    void callingRerankWithoutAKeyFailsFastWithoutTouchingTheEndpoint() {
        properties.getRetrieval().getRerank().setApiKey("");
        RagRerankUnavailableException ex = assertThrows(RagRerankUnavailableException.class,
                () -> client().rerank("问题", documents(3), 2));
        assertTrue(ex.getMessage().contains("未启用或未配置"), "message was: " + ex.getMessage());
        assertEquals(0, requestCount.get(), "an unconfigured re-ranker must not reach the network");
    }

    @Test
    void anEmptyDocumentListIsAFastNoOp() {
        assertTrue(client().rerank("问题", List.of(), 3).isEmpty());
        assertEquals(0, requestCount.get());
    }

    // ----------------------------------------------------------------- parsing

    @Test
    void indicesComeBackInRelevanceOrder() {
        assertEquals(List.of(2, 0, 1), client().rerank("问题", documents(3), 3));
    }

    @Test
    void topNCapsHowManyIndicesAreReturned() {
        assertEquals(List.of(2, 0), client().rerank("问题", documents(3), 2));
    }

    @Test
    void indicesOutsideTheDocumentRangeAreDroppedInsteadOfCorruptingTheResult() {
        responseBody = "{\"output\":{\"results\":[{\"index\":0},{\"index\":9},{\"index\":-1},{\"index\":1}]}}";
        assertEquals(List.of(0, 1), client().rerank("问题", documents(2), 5));
    }

    @Test
    void duplicateIndicesAreCollapsed() {
        responseBody = "{\"output\":{\"results\":[{\"index\":1},{\"index\":1},{\"index\":0}]}}";
        assertEquals(List.of(1, 0), client().rerank("问题", documents(2), 5));
    }

    @Test
    void aResponseWithoutResultsIsTreatedAsUnavailable() {
        responseBody = "{\"output\":{\"results\":[]}}";
        assertThrows(RagRerankUnavailableException.class, () -> client().rerank("问题", documents(2), 2));
    }

    // ---------------------------------------------------------------- protocol

    @Test
    void theRequestUsesTheDashScopeNativeBodyShape() {
        client().rerank("低保怎么申请", documents(3), 2);

        assertTrue(lastRequestBody.contains("\"model\":\"gte-rerank-v2\""), lastRequestBody);
        assertTrue(lastRequestBody.contains("\"query\":\"低保怎么申请\""), lastRequestBody);
        assertTrue(lastRequestBody.contains("\"return_documents\":false"), lastRequestBody);
        assertTrue(lastRequestBody.contains("\"top_n\":2"), lastRequestBody);
        assertTrue(lastRequestBody.contains("文档0"), lastRequestBody);
        assertEquals("Bearer test-key", lastAuthHeader);
    }

    @Test
    void topNIsClampedToTheDocumentCount() {
        client().rerank("问题", documents(2), 10);
        assertTrue(lastRequestBody.contains("\"top_n\":2"), lastRequestBody);
    }

    @Test
    void aNonOkStatusSurfacesTheCodeAndTheBodySnippet() {
        responseStatus = 429;
        responseBody = "{\"code\":\"Throttling\",\"message\":\"Requests throttled\"}";
        RagRerankUnavailableException ex = assertThrows(RagRerankUnavailableException.class,
                () -> client().rerank("问题", documents(2), 2));
        assertTrue(ex.getMessage().contains("HTTP 429"), "message was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Throttling"), "message was: " + ex.getMessage());
    }

    @Test
    void aDeadEndpointSurfacesAsUnavailableAfterRetrying() {
        properties.getRetrieval().getRerank().setBaseUrl("http://127.0.0.1:1/rerank");
        RagRerankUnavailableException ex = assertThrows(RagRerankUnavailableException.class,
                () -> client().rerank("问题", documents(2), 2));
        assertTrue(ex.getMessage().contains("重排请求失败"), "message was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("http://127.0.0.1:1/rerank"),
                "the message must name the endpoint so the failure is locatable: " + ex.getMessage());
    }
}

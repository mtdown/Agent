package com.et.cloud.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmRagQueryExpansionClientTest {

    private HttpServer server;
    private RagProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        properties = new RagProperties();
        properties.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getLlm().setApiKey("test-key");
        properties.getRetrieval().getMultiQuery().setEnabled(true);
        properties.getRetrieval().getMultiQuery().setModel("qwen3.8-flash");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void disablesThinkingForFastQueryExpansion() {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "{\"choices\":[{\"message\":{\"content\":\"{\\\"rewrittenQuestion\\\":\\\"rewrite\\\",\\\"hypotheticalAnswer\\\":\\\"answer\\\"}\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();

        new LlmRagQueryExpansionClient(properties).expand("original question");

        assertTrue(requestBody.get().contains("\"enable_thinking\":false"),
                "query expansion must disable thinking mode so qwen3.8-flash stays in the fast path");
    }
}

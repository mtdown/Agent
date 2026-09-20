package com.et.cloud.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagLlmClientTest {

    private HttpServer server;
    private RagProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        properties = new RagProperties();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void serve(String responseBody, int status) {
        server.createContext("/chat/completions", exchange -> {
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        properties.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getLlm().setApiKey("test-key");
        properties.getLlm().setModel("thinking-model");
        properties.getLlm().setFastModel("fast-model");
    }

    private static class RecordingCallback implements RagLlmClient.StreamCallback {
        final StringBuilder reasoning = new StringBuilder();
        final StringBuilder content = new StringBuilder();
        String finishReason;
        RagLlmClient.Usage usage;
        String error;

        @Override
        public void onReasoningDelta(String text) {
            reasoning.append(text);
        }

        @Override
        public void onContentDelta(String text) {
            content.append(text);
        }

        @Override
        public void onFinished(String reason, RagLlmClient.Usage usage) {
            this.finishReason = reason;
            this.usage = usage;
        }

        @Override
        public void onError(String message) {
            this.error = message;
        }
    }

    @Test
    void parsesInterleavedReasoningAndContentDeltas() {
        serve("data: {\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":null,\"reasoning_content\":\"思考\"}}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"渝府办发\",\"reasoning_content\":null}}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"〔2026〕24号\",\"reasoning_content\":\"\"}}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: {\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":35},\"choices\":[]}\n\n"
                + "data: [DONE]\n\n", 200);
        RagLlmClient client = new RagLlmClient(properties);
        RecordingCallback callback = new RecordingCallback();
        client.streamChat("system", "prompt", true, callback);
        assertEquals("思考", callback.reasoning.toString());
        assertEquals("渝府办发〔2026〕24号", callback.content.toString());
        assertEquals("stop", callback.finishReason);
        assertEquals(120L, callback.usage.promptTokens);
        assertEquals(35L, callback.usage.completionTokens);
        assertNull(callback.error);
    }

    @Test
    void malformedLinesAreSkipped() {
        serve(": keep-alive comment\n\n"
                + "data: not-json\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"正常\"}}]}\n\n"
                + "data: [DONE]\n\n", 200);
        RagLlmClient client = new RagLlmClient(properties);
        RecordingCallback callback = new RecordingCallback();
        client.streamChat("system", "prompt", false, callback);
        assertEquals("正常", callback.content.toString());
        assertEquals("stop", callback.finishReason);
        assertNull(callback.usage);
    }

    @Test
    void non200ReportsError() {
        serve("{\"error\":{\"message\":\"bad key\"}}", 401);
        RagLlmClient client = new RagLlmClient(properties);
        RecordingCallback callback = new RecordingCallback();
        client.streamChat("system", "prompt", false, callback);
        assertTrue(callback.error != null && callback.error.contains("401"));
        assertNull(callback.finishReason);
    }

    @Test
    void missingKeyReportsErrorWithoutHttpCall() {
        properties.getLlm().setApiKey("");
        RagLlmClient client = new RagLlmClient(properties);
        RecordingCallback callback = new RecordingCallback();
        client.streamChat("system", "prompt", false, callback);
        assertTrue(callback.error != null && callback.error.contains("RAG_LLM_API_KEY"));
    }

    @Test
    void deepThinkingFlagSelectsModel() throws Exception {
        final String[] requestedModel = {null};
        server.createContext("/chat/completions", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String json = new String(body, StandardCharsets.UTF_8);
            // crude but sufficient: model field appears before messages
            int i = json.indexOf("\"model\":\"");
            requestedModel[0] = i < 0 ? null : json.substring(i + 9, json.indexOf('"', i + 9));
            byte[] out = "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        properties.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getLlm().setApiKey("test-key");
        properties.getLlm().setModel("thinking-model");
        properties.getLlm().setFastModel("fast-model");
        RagLlmClient client = new RagLlmClient(properties);

        client.streamChat("system", "prompt", false, new RecordingCallback());
        assertEquals("fast-model", requestedModel[0]);

        client.streamChat("system", "prompt", true, new RecordingCallback());
        assertEquals("thinking-model", requestedModel[0]);
    }

    @Test
    void enableThinkingIsOnlySentWhenExplicitlyConfigured() throws Exception {
        final java.util.List<String> bodies = new java.util.ArrayList<>();
        server.createContext("/chat/completions", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        properties.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getLlm().setApiKey("test-key");
        RagLlmClient client = new RagLlmClient(properties);

        // 未配置：请求体必须与该特性引入前逐字节一致，不得出现新字段
        client.streamChat("system", "prompt", false, new RecordingCallback());
        assertFalse(bodies.get(0).contains("enable_thinking"),
                "未配置时不得下发 enable_thinking，否则会污染既有厂商链路");

        properties.getLlm().setEnableThinking(false);
        client.streamChat("system", "prompt", false, new RecordingCallback());
        assertTrue(bodies.get(1).contains("\"enable_thinking\":false"));

        properties.getLlm().setEnableThinking(true);
        client.streamChat("system", "prompt", false, new RecordingCallback());
        assertTrue(bodies.get(2).contains("\"enable_thinking\":true"));
    }

    @Test
    void retriesOnceWhenConnectionFailsBeforeOutput() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/chat/completions", exchange -> {
            if (calls.incrementAndGet() == 1) {
                // abrupt close without any response: client sees IOException (connection reset)
                exchange.close();
                return;
            }
            byte[] out = ("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"重试成功\"},\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        properties.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getLlm().setApiKey("test-key");
        properties.getLlm().setModel("thinking-model");
        properties.getLlm().setFastModel("fast-model");
        RagLlmClient client = new RagLlmClient(properties);
        RecordingCallback callback = new RecordingCallback();

        client.streamChat("system", "prompt", false, callback);

        assertEquals(2, calls.get());
        assertEquals("重试成功", callback.content.toString());
        assertEquals("stop", callback.finishReason);
        assertNull(callback.error);
    }

    @Test
    void doesNotRetryAfterPartialOutput() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/chat/completions", exchange -> {
            if (calls.incrementAndGet() == 1) {
                // stream one delta, then break the connection mid-stream
                byte[] partial = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"部分\"}}]}\n\n"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                OutputStream os = exchange.getResponseBody();
                os.write(partial);
                os.flush();
                exchange.close(); // no finish_reason, no [DONE]
                return;
            }
            byte[] out = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"不该出现\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        properties.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getLlm().setApiKey("test-key");
        properties.getLlm().setModel("thinking-model");
        properties.getLlm().setFastModel("fast-model");
        RagLlmClient client = new RagLlmClient(properties);
        RecordingCallback callback = new RecordingCallback();

        client.streamChat("system", "prompt", false, callback);

        assertEquals(1, calls.get());
        assertEquals("部分", callback.content.toString());
        assertTrue(callback.error != null, "partial-output failure must surface an error");
    }
}

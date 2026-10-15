package com.et.agent;

import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiStreamModelTest {
    @Test void requestsBoundedNonThinkingFlashAnswer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"2\"},\"finish_reason\":\"stop\"}]}\n\n" +
                    "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            OpenAiStreamModel model = new OpenAiStreamModel("http://127.0.0.1:" + server.getAddress().getPort(), "test", "deepseek-flash", 2000);
            List<String> output = new ArrayList<>();
            model.stream(Collections.singletonList(new AgentLoop.Message("user", "1+1=?", null, null)), output::add, () -> false);
            assertEquals(Collections.singletonList("2"), output);
            com.fasterxml.jackson.databind.JsonNode body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(requestBody.get());
            assertEquals("disabled", body.path("thinking").path("type").asText());
            assertEquals(2048, body.path("max_tokens").asInt());
        } finally {
            server.stop(0);
        }
    }
    @Test void stopsWaitingForStalledSseBodyAtDeadline() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try {
                exchange.getResponseBody().write("data: ".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            OpenAiStreamModel model = new OpenAiStreamModel("http://127.0.0.1:" + server.getAddress().getPort(), "test", "test", 200);
            long started = System.nanoTime();
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> model.stream(Collections.singletonList(new AgentLoop.Message("user", "hi", null, null)), text -> {}, () -> false));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMs < 1500, "stalled SSE must stop at the model deadline, took " + elapsedMs + " ms");
            assertTrue(error.getMessage().contains("timed out"), error.getMessage());
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
    @Test void parsesTextFragmentedToolsAndUsage() {
        List<String> chunks = new ArrayList<>();
        OpenAiStreamModel.Parser parser = new OpenAiStreamModel.Parser(chunks::add);
        parser.accept("data: {\"choices\":[{\"delta\":{\"content\":\"A\"}}]}");
        parser.accept("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\",\"function\":{\"name\":\"look\",\"arguments\":\"{\\\"a\\\":\"}}]}}]}");
        parser.accept("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"name\":\"up\",\"arguments\":\"1}\"}}]},\"finish_reason\":\"tool_calls\"}]}");
        parser.accept("data: {\"choices\":[],\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":3}}");
        parser.accept("data: [DONE]");
        AgentLoop.Turn result = parser.finish();
        assertEquals(Collections.singletonList("A"), chunks);
        assertEquals("lookup", result.toolCalls.get(0).name);
        assertEquals("{\"a\":1}", result.toolCalls.get(0).arguments);
        assertEquals(7, result.usage.totalTokens);
    }

    @Test void rejectsMalformedEvent() {
        OpenAiStreamModel.Parser parser = new OpenAiStreamModel.Parser(text -> {});
        assertThrows(IllegalArgumentException.class, () -> parser.accept("data: {broken"));
    }
}

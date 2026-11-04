package com.et.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Helper for a local baseline only. No real model is called. */
class AgentWikiBaselineAgentIT {
    @Test void rawWikiIdentifiersAreRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AgentWikiBoundary().argumentsFor("create_wiki_document", "{\"spaceId\":\"1\",\"folderId\":\"2\"}"));
        assertEquals("Invalid or expired Wiki location reference", error.getMessage());
    }
    @Test void postCommitLostResponseBlocksNextWrite() throws Exception {
        String endpoint = required("AWIB_CLOUD_BASE");
        URI uri = URI.create(endpoint);
        assertEquals("http", uri.getScheme());
        assertEquals("127.0.0.1", uri.getHost());
        assertTrue(uri.getPort() > 0);
        assertTrue(uri.getPath().isEmpty());
        assertNull(uri.getUserInfo());
        assertNull(uri.getQuery());
        assertNull(uri.getFragment());
        String user = required("AWIB_USER_ID");
        String space = required("AWIB_SPACE_ID");
        String folder = required("AWIB_FOLDER_ID");
        String key = required("AWIB_INTERNAL_KEY");
        String marker = required("AWIB_RUN_MARKER");
        assertTrue(marker.matches("awib-[a-f0-9]{32}"));
        assertTrue(user.matches("[1-9][0-9]{0,18}"));
        assertTrue(space.matches("[1-9][0-9]{0,18}"));
        assertTrue(folder.matches("[1-9][0-9]{0,18}"));
        String content = Files.readString(Path.of(required("AWIB_SAMPLE_PATH")), StandardCharsets.UTF_8);
        assertFalse(content.isBlank());

        HttpServer proxy = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        AtomicInteger forwarded = new AtomicInteger();
        AtomicInteger confirmed = new AtomicInteger();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> identity = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/awib-test-identity"))
                        .timeout(Duration.ofSeconds(3)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, identity.statusCode());
        assertEquals(marker, identity.body());
        proxy.createContext("/api/internal/agent/wiki-locations", exchange -> {
            byte[] out = new ObjectMapper().writeValueAsBytes(java.util.Map.of("code", 0, "data",
                    java.util.Map.of("spaces", java.util.List.of(java.util.Map.of("id", space)),
                            "folders", java.util.List.of(java.util.Map.of("id", folder)))));
            exchange.sendResponseHeaders(200, out.length);
            try (java.io.OutputStream stream = exchange.getResponseBody()) { stream.write(out); }
        });
        proxy.createContext("/api/internal/agent/wiki-documents/create", exchange -> {
            try {
                byte[] request = exchange.getRequestBody().readAllBytes();
                forwarded.incrementAndGet();
                HttpRequest forward = HttpRequest.newBuilder(URI.create(endpoint + exchange.getRequestURI()))
                        .timeout(Duration.ofSeconds(10)).header("X-Agent-Key", key)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(request)).build();
                HttpResponse<byte[]> result = http.send(forward, HttpResponse.BodyHandlers.ofByteArray());
                if (result.statusCode() == 200 && new ObjectMapper().readTree(result.body()).path("code").asInt(-1) == 0) {
                    confirmed.incrementAndGet();
                }
                // Deliberately discard the complete Cloud response and terminate without a response body.
                exchange.sendResponseHeaders(200, -1);
            } catch (Exception e) {
                try { exchange.sendResponseHeaders(503, -1); } catch (Exception ignored) { }
            } finally { exchange.close(); }
        });
        proxy.start();
        try {
            StringBuilder records = new StringBuilder("group,cloud_success_response,forwarded_requests,agent_status,second_write_blocked\n");
            for (int i = 0; i < 30; i++) {
                final int group = i;
                AtomicInteger turns = new AtomicInteger();
                AtomicInteger blocked = new AtomicInteger();
                String[] writeArgs = new String[1];
                AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway(
                        "http://127.0.0.1:" + proxy.getAddress().getPort(), key, Duration.ofSeconds(15));
                AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
                    int turn = turns.getAndIncrement();
                    if (turn == 0) return new AgentLoop.Turn(Collections.singletonList(
                            new AgentLoop.ToolCall("locations", "list_writable_wiki_locations", "{}")), null);
                    if (turn == 1) {
                        try {
                            com.fasterxml.jackson.databind.JsonNode locations = new ObjectMapper().readTree(history.get(history.size() - 1).content);
                            String spaceAlias = locations.path("spaces").get(0).path("id").asText();
                            String folderAlias = locations.path("folders").get(0).path("id").asText();
                            writeArgs[0] = new ObjectMapper().createObjectNode().put("spaceId", spaceAlias)
                                    .put("folderId", folderAlias).put("title", marker + "-natural-" + group)
                                    .put("content", content).toString();
                        } catch (Exception e) { throw new AssertionError("Could not read local fixture aliases", e); }
                    }
                    if (turn == 2) {
                        String result = history.get(history.size() - 1).content;
                        boolean unknown = result.contains("\"status\":\"unknown\"");
                        boolean invalid = result.contains("Invalid tool arguments");
                        boolean operationFailed = result.contains("Tool operation failed");
                        assertTrue(unknown, "unknown=" + unknown + ", invalid=" + invalid
                                + ", operationFailed=" + operationFailed + ", forwarded=" + forwarded.get()
                                + ", confirmed=" + confirmed.get());
                    }
                    if (turn == 3) {
                        if (history.get(history.size() - 1).content.contains("blocked")) blocked.incrementAndGet();
                        return new AgentLoop.Turn(Collections.emptyList(), null);
                    }
                    return new AgentLoop.Turn(Collections.singletonList(
                            new AgentLoop.ToolCall(Integer.toString(turn), "create_wiki_document", writeArgs[0])), null);
                }, AgentToolRegistry.bind(user, gateway), 5, 60000, 3000);
                int before = forwarded.get();
                int beforeConfirmed = confirmed.get();
                loop.run("baseline", chunk -> { }, () -> false);
                assertEquals(1, forwarded.get() - before);
                assertEquals(1, confirmed.get() - beforeConfirmed);
                assertEquals(1, blocked.get());
                records.append(group).append(',').append(confirmed.get() - beforeConfirmed).append(',')
                        .append(forwarded.get() - before).append(",unknown,").append(blocked.get()).append('\n');
            }
            Files.writeString(Path.of(required("AWIB_AGENT_RECORD_PATH")), records.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } finally { proxy.stop(0); }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing required test environment: " + name);
        return value;
    }
}

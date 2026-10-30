package com.et.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class AgentLibraryHttpGatewayTest {
    @Test void readsReservedBuiltInWikiSkillThroughCloud() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> request = new AtomicReference<>();
        server.createContext("/api/internal/agent/skills/", exchange -> {
            request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            byte[] response = "{\"code\":0,\"data\":{\"id\":\"builtin:wiki-write\",\"version\":1}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            assertTrue(gateway.getSkill("7", "builtin:wiki-write", 1).contains("builtin:wiki-write"));
            assertTrue(request.get().contains("GET /api/internal/agent/skills/builtin:wiki-write/versions/1?userId=7"));
            assertThrows(IllegalArgumentException.class, () -> gateway.getSkill("7", "builtin:unknown", 1));
        } finally { server.stop(0); }
    }
    @Test void createsWikiItemsThroughUserBoundCloudEndpoints() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CopyOnWriteArrayList<String> requests = new CopyOnWriteArrayList<>();
        server.createContext("/api/internal/agent/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("X-Agent-Key") + " "
                    + exchange.getRequestHeaders().getFirst("Content-Type") + " " + body);
            byte[] response = ("{\"code\":0,\"data\":{\"spaceId\":\"88\","
                    + "\"folderId\":\"9007199254740995\",\"documentId\":\"9007199254740997\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            assertTrue(gateway.createWikiFolder("7", "88", "中文任务").contains("\"folderId\":\"9007199254740995\""));
            assertTrue(gateway.createWikiDocument("7", "88", "9007199254740995", "中文标题", "正文")
                    .contains("\"documentId\":\"9007199254740997\""));
            assertTrue(gateway.writeWikiDocument("7", "88", "旧标题", "正文")
                    .contains("\"folderId\":\"9007199254740995\""));
            assertEquals(3, requests.size());
            assertTrue(requests.get(0).contains("POST /api/internal/agent/wiki-folders?userId=7 secret application/json; charset=UTF-8"));
            assertTrue(requests.get(0).contains("\"taskName\":\"中文任务\""));
            assertTrue(requests.get(1).contains("POST /api/internal/agent/wiki-documents/create?userId=7 secret"));
            assertTrue(requests.get(1).contains("\"folderId\":\"9007199254740995\""));
            assertTrue(requests.get(2).contains("POST /api/internal/agent/wiki-documents?userId=7 secret"));
        } finally { server.stop(0); }
    }

    @Test void readsWritableWikiLocationsWithBoundUserAndCursor() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> request = new AtomicReference<>();
        server.createContext("/api/internal/agent/wiki-locations", exchange -> {
            request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("X-Agent-Key"));
            byte[] response = "{\"code\":0,\"data\":{\"spaces\":[],\"folders\":[],\"nextCursor\":null}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            assertTrue(gateway.listWritableWikiLocations("7", "11", "9007199254740993", 25)
                    .contains("\"folders\":[]"));
            assertTrue(request.get().contains("GET /api/internal/agent/wiki-locations?spaceId=11&cursor=9007199254740993&limit=25&userId=7 secret"));
            AgentLoop.Tool tool = AgentToolRegistry.bind("7", gateway).get("list_writable_wiki_locations");
            assertNotNull(tool);
            assertTrue(tool.execute("{\"spaceId\":\"11\",\"cursor\":\"9007199254740993\",\"limit\":25}")
                    .contains("\"spaces\":[]"));
            assertFalse(AgentToolRegistry.declaration("list_writable_wiki_locations")
                    .path("function").path("parameters").path("properties").has("userId"));
            assertThrows(IllegalArgumentException.class,
                    () -> gateway.listWritableWikiLocations("7", "11", null, 101));
        } finally { server.stop(0); }
    }
    @Test void writesAndReadsPersonalFilesThroughApiPrefix() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> request = new AtomicReference<>();
        server.createContext("/api/internal/agent/files", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("X-Agent-Key") + " " + body);
            byte[] response = "{\"code\":0,\"data\":{\"path\":\"notes/a.txt\",\"content\":\"saved\",\"verified\":true},\"message\":\"ok\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            assertTrue(gateway.writeFile("7", "notes/a.txt", "saved", "create").contains("\"verified\":true"));
            assertTrue(request.get().contains("POST /api/internal/agent/files?userId=7 secret"));
            assertTrue(request.get().contains("\"mode\":\"create\""));
            assertTrue(gateway.readFile("7", "notes/a.txt").contains("\"content\":\"saved\""));
            assertTrue(request.get().contains("GET /api/internal/agent/files?path=notes%2Fa.txt&userId=7 secret"));
        } finally { server.stop(0); }
    }

    @Test void sendsCurrentUserAndInternalKeyToCloudLibrary() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> request = new AtomicReference<>();
        server.createContext("/api/internal/agent", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("X-Agent-Key") + " " + body);
            byte[] response = "{\"code\":0,\"data\":\"9007199254740993\",\"message\":\"ok\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            assertEquals("9007199254740993", gateway.createSkill("7", "{\"name\":\"Example\"}"));
            assertTrue(request.get().contains("POST /api/internal/agent/skills?userId=7 secret"));
            assertTrue(request.get().contains("\"name\":\"Example\""));
            assertThrows(IllegalArgumentException.class, () -> gateway.createSkill("not-an-id", "{}"));
        } finally {
            server.stop(0);
        }
    }

    @Test void rejectsCloudBusinessErrorInsteadOfClaimingSaved() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/agent/codes", exchange -> {
            byte[] response = "{\"code\":40000,\"data\":null,\"message\":\"Invalid code entrypoint\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> gateway.createCode("7", "{}"));
            assertTrue(error.getMessage().contains("Invalid code entrypoint"));
        } finally {
            server.stop(0);
        }
    }

    @Test void businessErrorsReachToolModelWithTheirCodes() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicInteger code = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/api/internal/agent/files", exchange -> {
            byte[] response = ("{\"code\":" + code.get() + ",\"data\":null,\"message\":\"请求被拒绝\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway("http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            for (int value : new int[]{40000, 40101, 40400}) {
                code.set(value);
                String result = runFileTool(gateway, "read_file", "{\"path\":\"notes/a.txt\"}");
                com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
                assertEquals(value, json.path("code").asInt());
                assertEquals("请求被拒绝", json.path("message").asText());
            }
        } finally { server.stop(0); }
    }

    private static String runFileTool(AgentLibraryHttpGateway gateway, String name, String args) {
        java.util.concurrent.atomic.AtomicInteger turns = new java.util.concurrent.atomic.AtomicInteger();
        AtomicReference<String> result = new AtomicReference<>();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            if (turns.getAndIncrement() == 0) return new AgentLoop.Turn(java.util.Collections.singletonList(new AgentLoop.ToolCall("1", name, args)), null);
            result.set(history.get(history.size() - 1).content);
            return new AgentLoop.Turn(java.util.Collections.emptyList(), null);
        }, AgentToolRegistry.bind("7", gateway), 3, 60000, 2000);
        loop.run("test", chunk -> {}, () -> false);
        return result.get();
    }

    @Test void responseTimeoutDistinguishesWriteAndReadWithoutRetry() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.ExecutorService serverThreads = java.util.concurrent.Executors.newCachedThreadPool();
        java.util.concurrent.ExecutorService callers = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
        server.setExecutor(serverThreads);
        server.createContext("/api/internal/agent/files", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if ("POST".equals(exchange.getRequestMethod())) writes.incrementAndGet(); else reads.incrementAndGet();
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway("http://127.0.0.1:" + server.getAddress().getPort(), "secret", java.time.Duration.ofMillis(300));
            java.util.concurrent.Future<String> write = callers.submit(() -> runFileTool(gateway, "write_file", "{\"path\":\"a.txt\",\"content\":\"text\",\"mode\":\"create\"}"));
            java.util.concurrent.Future<String> read = callers.submit(() -> runFileTool(gateway, "read_file", "{\"path\":\"a.txt\"}"));
            com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode writeResult = json.readTree(write.get(30, java.util.concurrent.TimeUnit.SECONDS));
            com.fasterxml.jackson.databind.JsonNode readResult = json.readTree(read.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("unknown", writeResult.path("status").asText());
            assertFalse(writeResult.has("saved"));
            assertEquals(50001, writeResult.path("code").asInt());
            assertNotEquals("unknown", readResult.path("status").asText());
            assertEquals(1, reads.get());
            assertEquals(1, writes.get());
        } finally { server.stop(0); callers.shutdownNow(); serverThreads.shutdownNow(); }
    }

    @Test void modelCannotRepeatWriteAfterUnknownEvenWithChangedArgumentsButCanRead() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.ExecutorService threads = java.util.concurrent.Executors.newCachedThreadPool();
        java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        server.setExecutor(threads);
        server.createContext("/api/internal/agent/files", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if ("POST".equals(exchange.getRequestMethod())) {
                writes.incrementAndGet();
                try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                exchange.close();
            } else {
                reads.incrementAndGet();
                byte[] data = "{\"code\":0,\"data\":{\"content\":\"ok\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, data.length);
                try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(data); }
            }
        });
        server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway("http://127.0.0.1:" + server.getAddress().getPort(), "secret", java.time.Duration.ofMillis(400));
            java.util.concurrent.atomic.AtomicInteger turns = new java.util.concurrent.atomic.AtomicInteger();
            AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
                int n = turns.getAndIncrement();
                if (n == 0) return new AgentLoop.Turn(java.util.Collections.singletonList(new AgentLoop.ToolCall("1", "write_file", "{\"path\":\"a.txt\",\"content\":\"text\",\"mode\":\"create\"}")), null);
                if (n == 1) {
                    assertTrue(history.get(history.size() - 1).content.contains("\"status\":\"unknown\""));
                    return new AgentLoop.Turn(java.util.Arrays.asList(new AgentLoop.ToolCall("2", "write_file", "{\"path\":\"b.txt\",\"content\":\"text\",\"mode\":\"create\"}"), new AgentLoop.ToolCall("3", "read_file", "{\"path\":\"a.txt\"}")), null);
                }
                assertTrue(history.stream().anyMatch(m -> "2".equals(m.toolCallId) && m.content.contains("unknown")));
                assertTrue(history.stream().anyMatch(m -> "3".equals(m.toolCallId) && m.content.contains("ok")));
                return new AgentLoop.Turn(java.util.Collections.emptyList(), null);
            }, AgentToolRegistry.bind("7", gateway), 4, 10000, 3000);
            loop.run("test", chunk -> {}, () -> false);
            assertEquals(1, writes.get()); assertEquals(1, reads.get());
        } finally { server.stop(0); threads.shutdownNow(); }
    }

    @Test void cloudErrorDoesNotExposeInternalSecretsPathsOrIds() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/agent/files", exchange -> {
            byte[] response = "{\"code\":40101,\"data\":null,\"message\":\"secret-token /srv/private 9007199254740995\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(response); }
        }); server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway("http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            String result = runFileTool(gateway, "read_file", "{\"path\":\"a.txt\"}");
            assertEquals(40101, new com.fasterxml.jackson.databind.ObjectMapper().readTree(result).path("code").asInt());
            assertFalse(result.contains("secret-token")); assertFalse(result.contains("/srv/private")); assertFalse(result.contains("9007199254740995"));
        } finally { server.stop(0); }
    }

    @Test void uncertainResponsesAfterWriteBlockModelRetry() throws Exception {
        for (String mode : new String[]{"disconnect", "corrupt", "http", "missing", "nullData", "serverError", "invalidCode"}) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            java.util.concurrent.atomic.AtomicInteger received = new java.util.concurrent.atomic.AtomicInteger();
            server.createContext("/api/internal/agent/files", exchange -> {
                exchange.getRequestBody().readAllBytes(); received.incrementAndGet();
                if (!"disconnect".equals(mode)) {
                    String body = "corrupt".equals(mode) ? "{invalid" : "missing".equals(mode) ? "{\"code\":0}" : "nullData".equals(mode) ? "{\"code\":0,\"data\":null}" : "serverError".equals(mode) ? "{\"code\":50001,\"data\":null}" : "invalidCode".equals(mode) ? "{\"code\":\"oops\"}" : "error";
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders("http".equals(mode) ? 503 : 200, bytes.length);
                    try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
                } else exchange.close();
            }); server.start();
            try {
                AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway("http://127.0.0.1:" + server.getAddress().getPort(), "secret", java.time.Duration.ofSeconds(2));
                java.util.concurrent.atomic.AtomicInteger turns = new java.util.concurrent.atomic.AtomicInteger();
                AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
                    int n = turns.getAndIncrement();
                    if (n == 1) {
                        com.fasterxml.jackson.databind.JsonNode first;
                        try { first = new com.fasterxml.jackson.databind.ObjectMapper().readTree(history.get(history.size()-1).content); } catch (java.io.IOException e) { throw new AssertionError(e); }
                        assertEquals("unknown", first.path("status").asText(), mode);
                        assertFalse(first.has("saved"), mode);
                    }
                    if (n < 2) return new AgentLoop.Turn(java.util.Collections.singletonList(new AgentLoop.ToolCall(""+n, "write_file", "{\"path\":\"a.txt\",\"content\":\"x\",\"mode\":\"create\"}")), null);
                    assertTrue(history.get(history.size()-1).content.contains("blocked"), mode);
                    return new AgentLoop.Turn(java.util.Collections.emptyList(), null);
                }, AgentToolRegistry.bind("7", gateway), 4, 10000, 3000);
                loop.run("test", chunk -> {}, () -> false);
                assertEquals(1, received.get(), mode);
            } finally { server.stop(0); }
        }
    }

    @Test void explicitBusinessRejectionIsNotUnknown() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/agent/files", exchange -> {
            byte[] response = "{\"code\":40000,\"data\":null,\"message\":\"请求被拒绝\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(response); }
        }); server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway("http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            com.fasterxml.jackson.databind.JsonNode result = new com.fasterxml.jackson.databind.ObjectMapper().readTree(runFileTool(gateway, "write_file", "{\"path\":\"a.txt\",\"content\":\"x\",\"mode\":\"create\"}"));
            assertEquals(40000, result.path("code").asInt());
            assertFalse(result.path("saved").asBoolean(true));
            assertFalse(result.has("status"));
        } finally { server.stop(0); }
    }
    @Test void segmentAndSearchToolsUseAuthenticatedUserBoundHttp() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> request = new AtomicReference<>();
        server.createContext("/api/internal/agent/files", exchange -> {
            request.set(exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("X-Agent-Key"));
            byte[] response = "{\"code\":0,\"data\":{\"text\":\"中文\",\"next\":\"token\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(response); }
        }); server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway("http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            var tools = AgentToolRegistry.bind("7", gateway);
            assertTrue(tools.get("read_file_segment").execute("{\"path\":\"a.txt\"}").contains("中文"));
            assertTrue(request.get().contains("/files/segment?path=a.txt&userId=7 secret"));
            tools.get("search_file").execute("{\"path\":\"a.txt\",\"query\":\"中文\",\"cursor\":\"token\"}");
            assertTrue(request.get().contains("/files/search?path=a.txt&query="));
            assertTrue(request.get().contains("cursor=token&userId=7 secret"));
            assertThrows(IllegalArgumentException.class, () -> tools.get("search_file").execute("{\"path\":\"a.txt\",\"query\":\"x\",\"userId\":\"8\"}"));
        } finally { server.stop(0); }
    }

    @Test void typicalSegmentsContinueWithinDefaultLoopBudget() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/agent/files/segment", exchange -> {
            String text = "abc中文".repeat(200);
            String body = "{\"code\":0,\"data\":{\"text\":\"" + text + "\",\"next\":\"token\"}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
        }); server.start();
        try {
            var gateway = new AgentLibraryHttpGateway("http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            var turns = new java.util.concurrent.atomic.AtomicInteger();
            AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
                int n = turns.getAndIncrement();
                if (n < 2) return new AgentLoop.Turn(java.util.Collections.singletonList(new AgentLoop.ToolCall("" + n, "read_file_segment", n == 0 ? "{\"path\":\"a.txt\"}" : "{\"path\":\"a.txt\",\"cursor\":\"token\"}")), null);
                return new AgentLoop.Turn(java.util.Collections.emptyList(), null);
            }, AgentToolRegistry.bind("7", gateway), 4, 10000, 16000);
            loop.run("test", chunk -> {}, () -> false);
            assertEquals(3, turns.get());
        } finally { server.stop(0); }
    }

}

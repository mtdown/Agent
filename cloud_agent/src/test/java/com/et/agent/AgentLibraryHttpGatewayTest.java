package com.et.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AgentLibraryHttpGatewayTest {
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
}

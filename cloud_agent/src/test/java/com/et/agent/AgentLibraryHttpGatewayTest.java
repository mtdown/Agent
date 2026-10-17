package com.et.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AgentLibraryHttpGatewayTest {
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

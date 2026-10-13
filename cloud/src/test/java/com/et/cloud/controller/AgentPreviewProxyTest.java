package com.et.cloud.controller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AgentPreviewProxyTest {
    @Test void forwardsPrivateSseAndCredential() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> key = new AtomicReference<>();
        server.createContext("/internal/agent/stream", exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("X-Agent-Key"));
            byte[] data = "event: delta\ndata: {\"content\":\"ok\"}\n\nevent: done\ndata: {\"usage\":null}\n\n".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        });
        server.start();
        try {
            AgentPreviewProxy proxy = new AgentPreviewProxy("http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            proxy.stream("question").writeTo(output);
            assertEquals("secret", key.get());
            assertTrue(output.toString(StandardCharsets.UTF_8.name()).contains("event: delta"));
            assertTrue(output.toString(StandardCharsets.UTF_8.name()).contains("\"firstAnswerMs\":"));
            assertTrue(output.toString(StandardCharsets.UTF_8.name()).contains("\"totalMs\":"));
        } finally { server.stop(0); }
    }

    @Test void stopsForwardingWhenBrowserOutputCloses() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/stream", exchange -> {
            byte[] data = "event: delta\ndata: {\"content\":\"x\"}\n\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        });
        server.start();
        try {
            AgentPreviewProxy proxy = new AgentPreviewProxy("http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            OutputStream disconnected = new OutputStream() {
                @Override public void write(int value) throws IOException { throw new IOException("client closed"); }
                @Override public void write(byte[] data, int offset, int size) throws IOException { throw new IOException("client closed"); }
            };
            assertDoesNotThrow(() -> proxy.stream("q").writeTo(disconnected));
        } finally { server.stop(0); }
    }
}

package com.et.agent;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AgentWikiAnswerLoopTest {
    @Test void modelCanReadSkillAndUseCurrentEvidence() {
        AtomicInteger turn = new AtomicInteger();
        AgentLoop.ToolAwareModel model = (history, names, delta, cancelled) -> {
            assertTrue(history.stream().anyMatch(m -> m.content != null && m.content.contains("Current turn evidence:") && m.content.contains("[1]")));
            assertTrue(history.stream().anyMatch(m -> m.content != null && m.content.contains("builtin:wiki-answer@1")));
            if (turn.getAndIncrement() == 0)
                return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("id", "get_skill", "{\"id\":\"builtin:wiki-answer\",\"version\":1}")), null);
            assertTrue(history.stream().anyMatch(m -> "tool".equals(m.role) && m.content.contains("证据不足")));
            delta.accept("依据 Wiki [1]");
            return new AgentLoop.Turn(List.of(), null);
        };
        AgentLoop loop = new AgentLoop(model, Map.of(), 3, 10000, 10000);
        loop.run("根据 Wiki 回答", List.of(), "[1] 文档 正文", "builtin:wiki-answer@1", Map.of("get_skill", args -> {
            assertTrue(args.contains("builtin:wiki-answer"));
            return "{\"steps\":[{\"type\":\"response\",\"template\":\"证据不足时说明并引用\"}]}";
        }), s -> {}, () -> false);
        assertEquals(2, turn.get());
    }
    @Test void ordinaryConversationDoesNotForceSkill() {
        AtomicInteger tools = new AtomicInteger();
        AgentLoop.ToolAwareModel model = (history, names, delta, cancelled) -> {
            assertTrue(history.get(0).content.contains("If no Skill matches, answer normally"));
            delta.accept("你好"); return new AgentLoop.Turn(List.of(), null);
        };
        new AgentLoop(model, Map.of(), 3, 10000, 10000).run("你好", List.of(), null,
                "builtin:wiki-answer@1\nother@1", Map.of("get_skill", args -> { tools.incrementAndGet(); return "{}"; }), s -> {}, () -> false);
        assertEquals(0, tools.get());
    }
    @Test void gatewayReadsFixedVersionWithTrustedUser() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicReference<String> request = new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/api/internal/agent/skills/", exchange -> {
            request.set(exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("X-Agent-Key"));
            byte[] body = "{\"code\":0,\"data\":{\"id\":\"builtin:wiki-answer\",\"version\":1}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            AgentLibraryHttpGateway gateway = new AgentLibraryHttpGateway("http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            var tool = AgentToolRegistry.bind("7", gateway).get("get_skill");
            assertTrue(tool.execute("{\"id\":\"builtin:wiki-answer\",\"version\":1}").contains("builtin:wiki-answer"));
            assertTrue(request.get().contains("/builtin:wiki-answer/versions/1?userId=7 secret"));
            assertThrows(IllegalArgumentException.class, () -> tool.execute("{\"id\":\"builtin:wiki-answer\",\"version\":1,\"userId\":\"8\"}"));
        } finally { server.stop(0); }
    }
    @Test void anotherSkillCanBeSelected() {
        AtomicInteger count = new AtomicInteger();
        AgentLoop.ToolAwareModel model = (history, names, delta, cancelled) -> {
            if (count.getAndIncrement() == 0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("id", "get_skill", "{\"id\":\"123\",\"version\":1}")), null);
            return new AgentLoop.Turn(List.of(), null);
        };
        new AgentLoop(model, Map.of(), 3, 10000, 10000).run("执行其他任务", List.of(), null,
                "builtin:wiki-answer@1\n123@1", Map.of("get_skill", args -> { assertTrue(args.contains("123")); return "{}"; }), s -> {}, () -> false);
        assertEquals(2, count.get());
    }
}

package com.et.agent;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AgentToolTraceTest {
    @Test void completedTurnReportsOrderedSafeStatuses() {
        AtomicInteger n = new AtomicInteger();
        AgentLoop.Model model = (history, delta, cancel) -> n.getAndIncrement() == 0
                ? new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1", "read_file", "{\"path\":\"secret\"}"),
                  new AgentLoop.ToolCall("2", "write_file", "{}"), new AgentLoop.ToolCall("3", "create_code", "{}"),
                  new AgentLoop.ToolCall("4", "create_skill", "{}")), null)
                : new AgentLoop.Turn(List.of(), null);
        Map<String, AgentLoop.Tool> tools = Map.of("read_file", x -> "{\"content\":\"secret\"}",
                "write_file", x -> "{\"saved\":false,\"error\":\"private\"}",
                "create_code", x -> { throw new AgentLibraryHttpGateway.CloudFailure(50001, "private", true); }, "create_skill", x -> "{}");
        var result = new AgentLoop(model, tools, 3, 10000, 10000).run("q", x -> {}, () -> false);
        assertEquals(List.of("success", "failed", "unknown", "blocked"),
                result.toolTraces.stream().map(t -> t.status).collect(java.util.stream.Collectors.toList()));
        for (var trace : result.toolTraces) { assertTrue(trace.durationMs >= 0); assertFalse(trace.name.contains("secret")); }
    }

    @Test void failedModelTurnDoesNotEmitCompletedTraces() throws Exception {
        AtomicInteger n = new AtomicInteger();
        AgentLoop.Model model = (history, delta, cancel) -> {
            if (n.getAndIncrement() == 0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1", "read_file", "{\"path\":\"secret\"}")), null);
            throw new IllegalStateException("private");
        };
        AgentLibraryGateway gateway = org.mockito.Mockito.mock(AgentLibraryGateway.class);
        org.mockito.Mockito.when(gateway.readFile("7", "secret")).thenReturn("{\"content\":\"private\"}");
        AgentController controller = new AgentController(new AgentLoop(model, Map.of(), 3, 10000, 10000), "key", gateway);
        AgentController.Ask ask = new AgentController.Ask("q"); ask.userId = "7";
        var output = new java.io.ByteArrayOutputStream(); controller.stream("key", ask).writeTo(output);
        String stream = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(stream.contains("event: error")); assertFalse(stream.contains("event: done"));
        assertFalse(stream.contains("toolTraces")); assertFalse(stream.contains("private"));
    }
}

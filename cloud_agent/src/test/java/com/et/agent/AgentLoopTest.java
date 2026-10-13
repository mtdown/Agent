package com.et.agent;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AgentLoopTest {
    @Test void streamsFinalAnswerAndUsage() {
        List<String> chunks = new ArrayList<>();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            delta.accept("你"); delta.accept("好");
            return new AgentLoop.Turn(Collections.emptyList(), new AgentLoop.Usage(3, 2));
        }, Collections.emptyMap(), 3, 10000, 1000);
        AgentLoop.Result result = loop.run("问候", chunks::add, () -> false);
        assertEquals(Arrays.asList("你", "好"), chunks);
        assertEquals(5, result.usage.totalTokens);
        assertNotNull(result.firstAnswerMs);
    }

    @Test void returnsToolResultToNextTurn() {
        AtomicInteger turns = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            if (turns.getAndIncrement() == 0)
                return new AgentLoop.Turn(Collections.singletonList(new AgentLoop.ToolCall("1", "lookup", "{}")), new AgentLoop.Usage(2, 1));
            assertTrue(history.stream().anyMatch(m -> "tool".equals(m.role) && "found".equals(m.content)));
            delta.accept("done");
            return new AgentLoop.Turn(Collections.emptyList(), new AgentLoop.Usage(3, 2));
        }, Collections.singletonMap("lookup", json -> "found"), 3, 10000, 1000);
        AgentLoop.Result result = loop.run("q", text -> {}, () -> false);
        assertEquals(2, turns.get());
        assertEquals(8, result.usage.totalTokens);
    }

    @Test void rejectsUnknownTool() {
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> new AgentLoop.Turn(
                Collections.singletonList(new AgentLoop.ToolCall("1", "missing", "{}")), null),
                Collections.emptyMap(), 3, 10000, 1000);
        assertThrows(IllegalArgumentException.class, () -> loop.run("q", text -> {}, () -> false));
    }

    @Test void stopsAtTurnLimitBeforeAnotherTool() {
        AtomicInteger invoked = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> new AgentLoop.Turn(
                Collections.singletonList(new AgentLoop.ToolCall("1", "lookup", "{}")), null),
                Collections.singletonMap("lookup", json -> { invoked.incrementAndGet(); return "ok"; }), 1, 10000, 1000);
        assertThrows(IllegalStateException.class, () -> loop.run("q", text -> {}, () -> false));
        assertEquals(0, invoked.get());
    }

    @Test void cancellationStopsDeltas() {
        AtomicInteger chunks = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            delta.accept("one"); delta.accept("two");
            return new AgentLoop.Turn(Collections.emptyList(), null);
        }, Collections.emptyMap(), 2, 10000, 1000);
        assertThrows(AgentLoop.Cancelled.class, () -> loop.run("q", text -> chunks.incrementAndGet(), () -> chunks.get() > 0));
        assertEquals(1, chunks.get());
    }

    @Test void rejectsMalformedToolArgumentsWithoutExecution() {
        AtomicInteger invoked = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> new AgentLoop.Turn(
                Collections.singletonList(new AgentLoop.ToolCall("1", "lookup", "{broken")), null),
                Collections.singletonMap("lookup", json -> { invoked.incrementAndGet(); return "ok"; }), 2, 10000, 1000);
        assertThrows(IllegalArgumentException.class, () -> loop.run("q", text -> {}, () -> false));
        assertEquals(0, invoked.get());
    }

    @Test void expiresAfterModelTurn() {
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new AgentLoop.Turn(Collections.emptyList(), null);
        }, Collections.emptyMap(), 2, 1, 1000);
        assertThrows(IllegalStateException.class, () -> loop.run("q", text -> {}, () -> false));
    }
}

package com.et.agent;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AgentLoopTest {
    @Test void sendsOnlySuppliedHistoryAndCurrentEvidence() {
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            assertEquals("user", history.get(1).role);
            assertEquals("first question", history.get(1).content);
            assertEquals("assistant", history.get(2).role);
            assertTrue(history.get(3).content.contains("[1] source"));
            assertEquals("follow up", history.get(4).content);
            return new AgentLoop.Turn(Collections.emptyList(), null);
        }, Collections.emptyMap(), 2, 10000, 1000);
        loop.run("follow up", Arrays.asList(
                new AgentLoop.Message("user", "first question", null, null),
                new AgentLoop.Message("assistant", "first answer", null, null)),
                "[1] source", text -> {}, () -> false);
    }
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

    @Test void loadsSelectedSkillOnlyAfterToolCall() {
        AtomicInteger turns = new AtomicInteger();
        AgentLoop.ToolAwareModel model = new AgentLoop.ToolAwareModel() {
            @Override public AgentLoop.Turn stream(List<AgentLoop.Message> history, java.util.Set<String> toolNames,
                                                   java.util.function.Consumer<String> delta,
                                                   java.util.function.BooleanSupplier cancelled) {
                assertTrue(toolNames.contains("get_skill"));
                if (turns.getAndIncrement() == 0) {
                    assertTrue(history.get(0).content.contains("skill_system"));
                    assertTrue(history.get(1).content.contains("Example"));
                    assertFalse(history.get(1).content.contains("secret step"));
                    return new AgentLoop.Turn(Collections.singletonList(
                            new AgentLoop.ToolCall("call-1", "get_skill", "{\"id\":\"9007199254740993\",\"version\":1}")), null);
                }
                assertTrue(history.stream().anyMatch(item -> "tool".equals(item.role) && item.content.contains("secret step")));
                return new AgentLoop.Turn(Collections.emptyList(), null);
            }
        };
        AgentLoop loop = new AgentLoop(model, Collections.emptyMap(), 3, 10000, 1000);
        loop.run("use example", Collections.emptyList(), null, "Example", Collections.singletonMap(
                "get_skill", args -> "secret step"), chunk -> {}, () -> false);
        assertEquals(2, turns.get());
    }

    @Test void failedLibraryWriteIsReportedToModelWithoutClaimingSuccess() {
        AtomicInteger turns = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            if (turns.getAndIncrement() == 0)
                return new AgentLoop.Turn(Collections.singletonList(
                        new AgentLoop.ToolCall("write", "create_skill", "{}")), null);
            assertTrue(history.stream().anyMatch(item -> "tool".equals(item.role)
                    && item.content.contains("Code version or entrypoint does not exist")
                    && item.content.contains("\"saved\":false")));
            delta.accept("The draft was not saved");
            return new AgentLoop.Turn(Collections.emptyList(), null);
        }, Collections.singletonMap("create_skill", json -> {
            throw new IllegalArgumentException("Code version or entrypoint does not exist");
        }), 3, 10000, 1000);
        List<String> output = new ArrayList<>();
        loop.run("save draft", output::add, () -> false);
        assertEquals(Collections.singletonList("The draft was not saved"), output);
    }
}

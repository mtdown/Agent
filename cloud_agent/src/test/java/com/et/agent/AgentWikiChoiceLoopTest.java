package com.et.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentWikiChoiceLoopTest {
    @Test void choiceToolStopsBeforeCreationAndDoesNotStreamModelIds() {
        AtomicInteger turns = new AtomicInteger();
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            turns.incrementAndGet();
            delta.accept("个人区-1971148507794014209 ID: 1");
            return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("choice", "plan_wiki_destination",
                    "{\"destinationType\":\"unspecified\"}")), null);
        }, Map.of("plan_wiki_destination", raw -> "{\"choiceRequired\":true,"
                + "\"candidates\":[{\"id\":\"1\",\"name\":\"个人区-1971148507794014209\",\"type\":\"personal\"}]}"),
                6, 10_000, 10_000);
        List<String> visible = new ArrayList<>();
        loop.run("保存正文", visible::add, () -> false);
        assertEquals(1, turns.get());
        assertEquals(List.of(), visible);
    }

    @Test void ordinaryAgentAnswerStillReachesTheUser() {
        AgentLoop loop = new AgentLoop((history, delta, cancelled) -> {
            delta.accept("你好");
            return new AgentLoop.Turn(List.of(), null);
        }, Map.of("plan_wiki_destination", raw -> "{}"), 6, 10_000, 10_000);
        List<String> visible = new ArrayList<>();
        loop.run("你好", visible::add, () -> false);
        assertEquals(List.of("你好"), visible);
    }
}

package com.et.agent;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentWikiWorkflowLoopTest {
    @Test void defaultBudgetAllowsSkillDirectoryFolderDocumentAndFinalAnswer() throws Exception {
        try (InputStream source = getClass().getResourceAsStream("/application.yml")) {
            assertNotNull(source);
            assertTrue(new String(source.readAllBytes(), StandardCharsets.UTF_8)
                    .contains("max-turns: ${AGENT_MAX_TURNS:6}"));
        }
        AtomicInteger turn = new AtomicInteger();
        AgentLoop.ToolAwareModel model = new AgentLoop.ToolAwareModel() {
            String spaceAlias;
            @Override public AgentLoop.Turn stream(List<AgentLoop.Message> history, Set<String> toolNames,
                                                   java.util.function.Consumer<String> delta,
                                                   java.util.function.BooleanSupplier cancelled) {
                assertTrue(history.get(0).content.contains("builtin:wiki-write"));
                assertTrue(toolNames.containsAll(Set.of("get_skill", "list_writable_wiki_locations",
                        "plan_wiki_destination", "create_wiki_folder", "create_wiki_document")));
                switch (turn.getAndIncrement()) {
                    case 0: return call("get_skill", "{\"id\":\"builtin:wiki-write\",\"version\":1}");
                    case 1: return call("list_writable_wiki_locations", "{}");
                    case 2: return call("plan_wiki_destination", "{\"destinationType\":\"public\"}");
                    case 3:
                        spaceAlias = field(history.get(history.size() - 1).content, "spaceId");
                        assertNotEquals("1", spaceAlias);
                        return call("create_wiki_folder", "{\"spaceId\":\"" + spaceAlias + "\",\"taskName\":\"报告\"}");
                    case 4:
                        String folderAlias = field(history.get(history.size() - 1).content, "folderId");
                        assertNotEquals("21", folderAlias);
                        return call("create_wiki_document", "{\"spaceId\":\"" + spaceAlias + "\",\"folderId\":\"" + folderAlias + "\",\"content\":\"# 报告\\n正文\"}");
                    default:
                        assertTrue(history.stream().anyMatch(item -> "tool".equals(item.role)
                                && item.content.contains("\"documentId\"")));
                        delta.accept("已保存");
                        return new AgentLoop.Turn(List.of(), null);
                }
            }
        };
        Map<String, AgentLoop.Tool> tools = Map.of(
                "get_skill", args -> "{\"steps\":[]}",
                "list_writable_wiki_locations", args -> "{\"spaces\":[{\"id\":\"1\"}]}",
                "plan_wiki_destination", args -> "{\"choiceRequired\":false,\"spaceId\":\"1\"}",
                "create_wiki_folder", args -> {
                    assertEquals("1", field(args, "spaceId"));
                    return "{\"folderId\":\"21\"}";
                },
                "create_wiki_document", args -> {
                    assertEquals("1", field(args, "spaceId"));
                    assertEquals("21", field(args, "folderId"));
                    return "{\"documentId\":\"31\"}";
                });
        List<String> text = new ArrayList<>();
        AgentLoop loop = new AgentLoop(model, Map.of(), 6, 10_000, 10_000);
        AgentLoop.Result result = loop.run("将报告写入公开空间", List.of(), null, "builtin:wiki-write@1", tools,
                text::add, () -> false);
        assertEquals(6, turn.get());
        assertEquals(List.of(), text);
        assertTrue(result.wikiEvents.stream().anyMatch(item -> item.name.equals("create_wiki_document")
                && item.result.contains("\"documentId\"")));
    }

    private static String field(String json, String name) {
        try {
            String value = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).path(name).asText();
            assertFalse(value.isEmpty());
            return value;
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static AgentLoop.Turn call(String name, String args) {
        return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall(name, name, args)), null);
    }
}

package com.et.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentWikiPartialFailureTest {
    @Test void documentFailureLeavesFolderIdVisibleToNextModelTurn() {
        List<String> toolResults = new ArrayList<>();
        AgentLoop.Model model = (history, delta, cancelled) -> {
            for (AgentLoop.Message message : history) {
                if ("tool".equals(message.role) && !toolResults.contains(message.content))
                    toolResults.add(message.content);
            }
            if (toolResults.isEmpty()) return new AgentLoop.Turn(List.of(
                    new AgentLoop.ToolCall("folder-call", "create_wiki_folder", "{}")), null);
            if (toolResults.size() == 1) return new AgentLoop.Turn(List.of(
                    new AgentLoop.ToolCall("document-call", "create_wiki_document", "{}")), null);
            assertTrue(toolResults.get(0).contains("\"folderId\":\"9007199254740995\""));
            assertTrue(toolResults.get(1).contains("\"saved\":false"));
            assertFalse(toolResults.get(1).contains("documentId"));
            delta.accept("文档保存失败；已创建文件夹 9007199254740995。");
            return new AgentLoop.Turn(List.of(), null);
        };
        StringBuilder answer = new StringBuilder();
        AgentLoop loop = new AgentLoop(model, Map.of(
                "create_wiki_folder", args -> "{\"folderId\":\"9007199254740995\"}",
                "create_wiki_document", args -> { throw new IllegalStateException("Cloud rejected document"); }),
                4, 10_000, 4_000);
        loop.run("保存文档", answer::append, () -> false);
        assertEquals(2, toolResults.size());
        assertTrue(answer.toString().contains("保存失败"));
        assertTrue(answer.toString().contains("9007199254740995"));
        assertFalse(answer.toString().contains("文档已保存"));
    }
}

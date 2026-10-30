package com.et.agent;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentControllerWikiEventTest {
    @Test void emitsPrivateChoiceEventAndNoModelText() throws Exception {
        AgentLoop.Model model = (history, delta, cancelled) -> {
            delta.accept("个人区-1971148507794014209 ID: 11");
            return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1", "plan_wiki_destination",
                    "{\"destinationType\":\"unspecified\"}")), null);
        };
        AgentController controller = new AgentController(new AgentLoop(model, Map.of(), 6, 10_000, 10_000),
                "secret", gateway());
        AgentController.Ask ask = new AgentController.Ask("保存正文");
        ask.userId = "7";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        controller.stream("secret", ask).writeTo(out);
        String frames = out.toString(StandardCharsets.UTF_8.name());
        assertTrue(frames.contains("event: wiki_choice_request"));
        assertFalse(frames.contains("event: delta"));
        assertTrue(frames.contains("event: done"));
    }

    @Test void emitsToolActivityWithoutStreamingModelClaim() throws Exception {
        AtomicInteger turn = new AtomicInteger();
        AgentLoop.ToolAwareModel model = new AgentLoop.ToolAwareModel() {
            @Override public AgentLoop.Turn stream(List<AgentLoop.Message> history, Set<String> names,
                                                   java.util.function.Consumer<String> delta,
                                                   java.util.function.BooleanSupplier cancelled) {
                try {
                    var json=new com.fasterxml.jackson.databind.ObjectMapper();
                    switch (turn.getAndIncrement()) {
                        case 0: return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("0", "list_writable_wiki_locations", "{}")), null);
                        case 1: {
                            String space=json.readTree(history.get(history.size()-1).content).path("spaces").get(0).path("id").asText();
                            return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1", "create_wiki_folder",json.writeValueAsString(Map.of("spaceId",space,"taskName","任务")))),null);
                        }
                        case 2: {
                            var folder=json.readTree(history.get(history.size()-1).content);
                            return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("2", "create_wiki_document",json.writeValueAsString(Map.of("spaceId",folder.path("spaceId").asText(),"folderId",folder.path("folderId").asText(),"content","正文")))),null);
                        }
                        default: delta.accept("已保存"); return new AgentLoop.Turn(List.of(), null);
                    }
                } catch(Exception e) {throw new RuntimeException(e);}
            }
        };
        AgentController controller = new AgentController(new AgentLoop(model, Map.of(), 6, 10_000, 10_000),
                "secret", gateway());
        AgentController.Ask ask = new AgentController.Ask("写到个人空间");
        ask.userId = "7";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        controller.stream("secret", ask).writeTo(out);
        String frames = out.toString(StandardCharsets.UTF_8.name());
        assertEquals(3, frames.split("event: wiki_tool_event", -1).length - 1);
        assertTrue(frames.contains("\"documentId\":\"31\""));
        assertFalse(frames.contains("event: delta"));
        assertTrue(frames.contains("event: done"));
    }

    private static AgentLibraryGateway gateway() {
        return new AgentLibraryGateway() {
            @Override public String getSkill(String user, String id, int version) { return "{}"; }
            @Override public String createSkill(String user, String definition) { throw new UnsupportedOperationException(); }
            @Override public String createCode(String user, String definition) { throw new UnsupportedOperationException(); }
            @Override public String writeFile(String user, String path, String content, String mode) { throw new UnsupportedOperationException(); }
            @Override public String readFile(String user, String path) { throw new UnsupportedOperationException(); }
            @Override public String writeWikiDocument(String user, String space, String title, String content) {
                throw new UnsupportedOperationException();
            }
            @Override public String listWritableWikiLocations(String user, String space, String cursor, Integer limit) {
                return "{\"defaultPersonalSpaceId\":\"11\",\"spaces\":[{\"id\":\"11\","
                        + "\"name\":\"个人区-1971148507794014209\",\"type\":\"personal\"}]}";
            }
            @Override public String createWikiFolder(String user, String space, String task) {
                return "{\"spaceId\":\"11\",\"folderId\":\"21\",\"name\":\"任务 2026-10-26\"}";
            }
            @Override public String createWikiDocument(String user, String space, String folder, String title, String content) {
                return "{\"spaceId\":\"11\",\"folderId\":\"21\",\"documentId\":\"31\",\"title\":\"标题\"}";
            }
        };
    }
}

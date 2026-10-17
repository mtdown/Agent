package com.et.agent;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgentControllerTest {
    private AgentController controller(AgentLoop.Model model) {
        return new AgentController(new AgentLoop(model, Collections.emptyMap(), 2, 10000, 1000), "test-secret",
                new AgentLibraryGateway() {
                    public String getSkill(String userId, String id, int version) { return "{}"; }
                    public String createSkill(String userId, String definition) { return "1"; }
                    public String createCode(String userId, String definition) { return "2"; }
                });
    }

    private static AgentController.Ask ask(String query) {
        AgentController.Ask ask = new AgentController.Ask(query);
        ask.userId = "7";
        return ask;
    }

    @Test void rejectsUnauthenticatedCall() {
        assertThrows(ResponseStatusException.class, () -> controller((h, d, c) -> null).stream("bad", ask("q")));
    }

    @Test void emitsDeltasAndExactlyOneDoneWithUnavailableUsage() throws Exception {
        AgentController controller = controller((h, d, c) -> {
            d.accept("a"); d.accept("b");
            return new AgentLoop.Turn(Collections.emptyList(), null);
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        controller.stream("test-secret", ask("q")).writeTo(out);
        String frames = out.toString(StandardCharsets.UTF_8.name());
        assertEquals(2, frames.split("event: delta", -1).length - 1);
        assertEquals(1, frames.split("event: done", -1).length - 1);
        assertTrue(frames.contains("\"usage\":null"));
        assertTrue(frames.contains("\"firstAnswerMs\":"));
    }

    @Test void emitsOneError() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        controller((h, d, c) -> { throw new IllegalStateException("broken"); })
                .stream("test-secret", ask("q")).writeTo(out);
        String frames = out.toString(StandardCharsets.UTF_8.name());
        assertEquals(1, frames.split("event: error", -1).length - 1);
        assertFalse(frames.contains("event: done"));
    }

    @Test void usesAuthenticatedUserContextToCreateCodeAndSkillDraft() throws Exception {
        AgentLibraryGateway gateway = new AgentLibraryGateway() {
            @Override public String getSkill(String userId, String id, int version) { return "{}"; }
            @Override public String createSkill(String userId, String definition) {
                assertEquals("7", userId);
                assertTrue(definition.contains("Example"));
                return "9007199254740993";
            }
            @Override public String createCode(String userId, String definition) {
                assertEquals("7", userId);
                assertTrue(definition.contains("sourceCode"));
                return "9007199254740994";
            }
        };
        AgentLoop.ToolAwareModel model = new AgentLoop.ToolAwareModel() {
            int turn;
            @Override public AgentLoop.Turn stream(List<AgentLoop.Message> history, java.util.Set<String> tools,
                    java.util.function.Consumer<String> delta, java.util.function.BooleanSupplier cancelled) {
                assertTrue(tools.contains("create_code"));
                assertTrue(tools.contains("create_skill"));
                if (turn++ == 0) {
                    assertTrue(history.get(0).content.contains("skill_system"));
                    return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("c1", "create_code",
                            "{\"name\":\"Example\",\"summary\":\"Example\",\"language\":\"PYTHON\","
                            + "\"entrypoint\":\"run\",\"sourceCode\":\"def run(): pass\","
                            + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}},"
                            + "\"outputSchema\":{\"type\":\"object\",\"properties\":{}}}")), null);
                }
                if (turn == 2) {
                    assertTrue(history.stream().anyMatch(message -> "tool".equals(message.role)
                            && message.content.contains("9007199254740994")));
                    return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("c2", "create_skill",
                            "{\"definition\":{\"name\":\"Example\"}}")), null);
                }
                assertTrue(history.stream().anyMatch(message -> "tool".equals(message.role)
                        && message.content.contains("9007199254740993")));
                delta.accept("Draft saved");
                return new AgentLoop.Turn(Collections.emptyList(), null);
            }
        };
        AgentController controller = new AgentController(new AgentLoop(model, Collections.emptyMap(), 4, 10000, 2000),
                "test-secret", gateway);
        AgentController.Ask ask = new AgentController.Ask("Save a skill and its code");
        ask.userId = "7";
        ask.skillCatalog = "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        controller.stream("test-secret", ask).writeTo(out);
        assertTrue(out.toString(StandardCharsets.UTF_8.name()).contains("Draft saved"));
    }

    @Test void rejectsMissingUserContextForLibraryTools() {
        AgentController controller = controller((h, d, c) -> null);
        assertThrows(ResponseStatusException.class,
                () -> controller.stream("test-secret", new AgentController.Ask("create a skill")));
    }
}

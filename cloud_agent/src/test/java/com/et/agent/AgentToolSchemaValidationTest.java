package com.et.agent;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentToolSchemaValidationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode sample(JsonNode schema) {
        if (schema.path("enum").isArray()) return schema.path("enum").get(0).deepCopy();
        switch (schema.path("type").asText()) {
            case "object":
                ObjectNode obj = JSON.createObjectNode();
                for (JsonNode name : schema.path("required")) obj.set(name.asText(), sample(schema.path("properties").path(name.asText())));
                return obj;
            case "array": return JSON.createArrayNode();
            case "integer": case "number": return JSON.getNodeFactory().numberNode(1);
            case "boolean": return JSON.getNodeFactory().booleanNode(true);
            default: return JSON.getNodeFactory().textNode("x");
        }
    }

    private static void assertInvalidCall(String name, JsonNode args) {
        AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
        AtomicInteger turns = new AtomicInteger();
        AgentLoop.Model model = (history, delta, cancelled) -> {
            if (turns.getAndIncrement() == 0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1", name, args.toString())), null);
            assertTrue(history.get(history.size() - 1).content.contains("\"code\":40000"), name + " " + args);
            return new AgentLoop.Turn(List.of(), null);
        };
        new AgentLoop(model, AgentToolRegistry.bind("7", gateway), 3, 10000, 10000).run("q", x -> {}, () -> false);
        verifyNoInteractions(gateway);
    }

    @Test void everyDeclaredPropertyDrivesLoopValidation() {
        for (String name : AgentToolRegistry.names()) {
            JsonNode schema = AgentToolRegistry.declaration(name).path("function").path("parameters");
            ObjectNode base = (ObjectNode) sample(schema);
            if (!schema.path("additionalProperties").asBoolean(true)) {
                ObjectNode bad = base.deepCopy().put("unexpected", true);
                assertInvalidCall(name, bad);
            }
            schema.path("properties").fields().forEachRemaining(field -> {
                ObjectNode bad = base.deepCopy();
                String type = field.getValue().path("type").asText();
                bad.set(field.getKey(), "boolean".equals(type) ? JSON.getNodeFactory().textNode("wrong") : JSON.getNodeFactory().booleanNode(true));
                assertInvalidCall(name, bad);
                if (field.getValue().path("enum").isArray()) {
                    bad = base.deepCopy().put(field.getKey(), "not-an-enum-value");
                    assertInvalidCall(name, bad);
                }
            });
        }
    }

    @Test void unknownToolAndExecutionExceptionDoNotExposeSensitiveValues() {
        AgentLoop unknown = new AgentLoop((history, delta, cancelled) -> new AgentLoop.Turn(
                List.of(new AgentLoop.ToolCall("1", "private-tool-name", "{}")), null), Map.of(), 3, 10000, 10000);
        Exception error = assertThrows(IllegalArgumentException.class, () -> unknown.run("q", x -> {}, () -> false));
        assertFalse(error.getMessage().contains("private-tool-name"));
        AtomicInteger turns = new AtomicInteger();
        AgentLoop.Model model = (history, delta, cancelled) -> {
            if (turns.getAndIncrement() == 0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1", "read_file", "{}")), null);
            assertFalse(history.get(history.size() - 1).content.contains("secret"));
            return new AgentLoop.Turn(List.of(), null);
        };
        Exception failure = assertThrows(IllegalStateException.class, () -> new AgentLoop(model,
                Map.of("read_file", args -> { throw new RuntimeException("secret"); }), 3, 10000, 10000)
                .run("q", x -> {}, () -> false));
        assertFalse(failure.getMessage().contains("secret"));
    }
    @Test void declarationsDriveRequiredChecks() {
        AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
        for (String name : AgentToolRegistry.names()) {
            if (AgentToolRegistry.declaration(name).path("function").path("parameters").path("required").isEmpty()) continue;
            assertThrows(IllegalArgumentException.class, () -> AgentToolRegistry.bind("7", gateway).get(name).execute("{}"), name);
        }
        verifyNoInteractions(gateway);
    }

    @Test void invalidArgumentsNeverInvokeGateway() {
        AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
        var tools = AgentToolRegistry.bind("7", gateway);
        for (String raw : new String[]{"{\"path\":\"a\",\"content\":\"x\",\"mode\":\"create\",\"extra\":1}", "{\"path\":\"a\",\"content\":\"x\",\"mode\":\"delete\"}", "{\"path\":\"a\",\"content\":true,\"mode\":\"create\"}"})
            assertThrows(IllegalArgumentException.class, () -> tools.get("write_file").execute(raw));
        assertThrows(IllegalArgumentException.class, () -> tools.get("get_skill").execute("{\"id\":\"a\",\"version\":true}"));
        assertThrows(IllegalArgumentException.class, () -> tools.get("create_skill").execute("{\"definition\":{\"name\":\"a\",\"summary\":\"a\",\"when_to_use\":\"a\",\"when_not_to_use\":\"a\",\"parameters\":[{\"name\":\"x\",\"type\":\"string\",\"required\":1}],\"steps\":[],\"expected_results\":[],\"failure_policy\":\"stop\",\"examples\":[]}}"));
        assertThrows(IllegalArgumentException.class, () -> tools.get("create_skill").execute("{\"definition\":{\"name\":\"a\",\"summary\":\"a\",\"when_to_use\":\"a\",\"when_not_to_use\":\"a\",\"parameters\":[],\"steps\":[{\"type\":\"response\",\"extra\":true}],\"expected_results\":[],\"failure_policy\":\"stop\",\"examples\":[]}}"));
        assertThrows(IllegalArgumentException.class, () -> AgentToolRegistry.bind("7", gateway, "123").get("plan_wiki_destination").execute("{\"destinationType\":\"bogus\"}"));
        verifyNoInteractions(gateway);
    }

    @Test void loopReportsSafeParameterCodeBeforeGateway() {
        AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
        AtomicInteger turns = new AtomicInteger();
        AgentLoop.Model model = (history, delta, cancelled) -> {
            if (turns.getAndIncrement() == 0)
                return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("x", "write_file", "{\"path\":\"secret\",\"content\":\"private\",\"mode\":\"invalid\"}")), null);
            String result = history.get(history.size() - 1).content;
            assertTrue(result.contains("\"code\":40000"));
            assertFalse(result.contains("private"));
            assertFalse(result.contains("secret"));
            return new AgentLoop.Turn(List.of(), null);
        };
        new AgentLoop(model, AgentToolRegistry.bind("7", gateway), 3, 10000, 10000).run("q", x -> {}, () -> false);
        verifyNoInteractions(gateway);
    }

    @Test void arbitraryNestedObjectsRemainAllowed() {
        AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
        when(gateway.createCode(anyString(), anyString())).thenReturn("123");
        String raw = "{\"name\":\"x\",\"summary\":\"x\",\"language\":\"PYTHON\",\"entrypoint\":\"x\",\"sourceCode\":\"x\",\"inputSchema\":{\"custom\":{\"nested\":[1,true]}},\"outputSchema\":{}}";
        assertDoesNotThrow(() -> AgentToolRegistry.bind("7", gateway).get("create_code").execute(raw));
        verify(gateway).createCode(eq("7"), anyString());
    }

    @Test void completeJsonAndRootTypeAreCheckedThroughLoop() {
        String valid = "{\"path\":\"a\",\"content\":\"x\",\"mode\":\"create\"}";
        for (String raw : List.of("[]", "null", "{broken", valid + " trailing", valid + " {}", valid + "\u0001")) {
            AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
            AtomicInteger turns = new AtomicInteger();
            AgentLoop.Model model = (history, delta, cancel) -> {
                if (turns.getAndIncrement() == 0) return new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1", "write_file", raw)), null);
                assertTrue(history.get(history.size() - 1).content.contains("\"code\":40000"), raw);
                return new AgentLoop.Turn(List.of(), null);
            };
            new AgentLoop(model, AgentToolRegistry.bind("7", gateway), 3, 10000, 10000).run("q", x -> {}, () -> false);
            verifyNoInteractions(gateway);
        }
        for (String raw : List.of(valid, valid + " \n\t")) {
            AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
            when(gateway.writeFile(anyString(), anyString(), anyString(), anyString())).thenReturn("{}");
            AtomicInteger turns = new AtomicInteger();
            AgentLoop.Model model = (history, delta, cancel) -> turns.getAndIncrement() == 0
                    ? new AgentLoop.Turn(List.of(new AgentLoop.ToolCall("1", "write_file", raw)), null)
                    : new AgentLoop.Turn(List.of(), null);
            new AgentLoop(model, AgentToolRegistry.bind("7", gateway), 3, 10000, 10000).run("q", x -> {}, () -> false);
            verify(gateway).writeFile("7", "a", "x", "create");
        }
    }

    @Test void integerVersionsPreserveMathematicalValueAndRejectOverflow() {
        AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
        when(gateway.getSkill("7", "x", 1)).thenReturn("{}");
        var tool = AgentToolRegistry.bind("7", gateway).get("get_skill");
        assertDoesNotThrow(() -> tool.execute("{\"id\":\"x\",\"version\":1.0}"));
        verify(gateway).getSkill("7", "x", 1);
        clearInvocations(gateway);
        assertThrows(IllegalArgumentException.class, () -> tool.execute("{\"id\":\"x\",\"version\":4294967297}"));
        assertThrows(IllegalArgumentException.class, () -> tool.execute("{\"id\":\"x\",\"version\":1.00000000000000001}"));
        verifyNoInteractions(gateway);
    }

    @Test void versionOverflowCannotReachGateway() {
        AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
        assertThrows(IllegalArgumentException.class, () -> AgentToolRegistry.bind("7", gateway).get("get_skill")
                .execute("{\"id\":\"x\",\"version\":4294967297}"));
        verifyNoInteractions(gateway);
    }

    @Test void skillCodeVersionIsExactAndInvalidVersionsNeverReachGateway() {
        String raw = "{\"definition\":{\"name\":\"a\",\"summary\":\"a\",\"when_to_use\":\"a\",\"when_not_to_use\":\"a\",\"parameters\":[],"
                + "\"steps\":[{\"type\":\"code\",\"code_item_id\":\"1\",\"code_version\":VERSION,\"entrypoint\":\"run\",\"args\":{}}],"
                + "\"expected_results\":[\"ok\"],\"failure_policy\":\"stop\",\"examples\":[{\"input\":{},\"expected\":\"ok\"}]}}";
        for (String version : List.of("1.1", "2147483648", "0", "-1", "1.00000000000000001")) {
            AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
            assertThrows(IllegalArgumentException.class, () -> AgentToolRegistry.bind("7", gateway).get("create_skill").execute(raw.replace("VERSION", version)));
            verifyNoInteractions(gateway);
        }
        for (String version : List.of("1.0", "1e0", "2147483647")) {
            AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
            when(gateway.createSkill(anyString(), anyString())).thenReturn("1");
            AgentToolRegistry.bind("7", gateway).get("create_skill").execute(raw.replace("VERSION", version));
            verify(gateway).createSkill(eq("7"), argThat(value -> {
                try { return JSON.readTree(value).path("steps").get(0).path("code_version").isInt(); }
                catch (Exception e) { return false; }
            }));
        }
    }
}

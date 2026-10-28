package com.et.cloud.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSkillDefinitionValidatorTest {
    private final AgentSkillDefinitionValidator validator = new AgentSkillDefinitionValidator();

    @Test
    void acceptsVersionedCodeStepWithStringId() {
        assertDoesNotThrow(() -> validator.validate(validDefinition(), (id, version, entrypoint) ->
                id.equals("9007199254740993") && version == 2 && entrypoint.equals("run")));
    }

    @Test
    void rejectsNumericIdAndMissingCodeVersion() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                validDefinition().replace("\"9007199254740993\"", "9007199254740993"), (id, version, entrypoint) -> true));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                validDefinition().replace("\"code_version\":2,", ""), (id, version, entrypoint) -> true));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                validDefinition(), (id, version, entrypoint) -> false));
    }

    @Test
    void rejectsUnknownStepAndInvalidParameterBounds() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                validDefinition().replace("\"type\":\"code\"", "\"type\":\"shell\""), (id, version, entrypoint) -> true));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                validDefinition().replace("\"min\":1,\"max\":5", "\"min\":6,\"max\":5"),
                (id, version, entrypoint) -> true));
    }

    @Test
    void rejectsUnknownFields() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                validDefinition().replace("\"failure_policy\":\"stop\"", "\"failure_policy\":\"stop\",\"bypass_permissions\":true"),
                (id, version, entrypoint) -> true));
    }

    @Test
    void identifiesUnknownParameterField() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> validator.validate(
                validDefinition().replace("\"required\":true", "\"required\":true,\"description\":\"extra\""),
                (id, version, entrypoint) -> true));
        assertTrue(error.getMessage().contains("parameters[0].description"), error.getMessage());
    }

    @Test
    void rejectsSkillStepReferencingUnregisteredTool() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                validDefinition().replace("\"type\":\"code\",\"code_item_id\":\"9007199254740993\",\"code_version\":2,\"entrypoint\":\"run\",\"args\":{\"count\":\"$count\"}",
                        "\"type\":\"tool\",\"tool\":\"shell\",\"args\":{}"),
                (id, version, entrypoint) -> true));
    }

    @Test
    void acceptsTheWikiWriteWorkflowTools() {
        for (String tool : new String[]{"list_writable_wiki_locations", "create_wiki_folder", "create_wiki_document"}) {
            String definition = validDefinition().replace(
                    "\"type\":\"code\",\"code_item_id\":\"9007199254740993\",\"code_version\":2,\"entrypoint\":\"run\",\"args\":{\"count\":\"$count\"}",
                    "\"type\":\"tool\",\"tool\":\"" + tool + "\",\"args\":{}");
            assertDoesNotThrow(() -> validator.validate(definition, (id, version, entrypoint) -> false));
        }
    }

    private static String validDefinition() {
        return "{\"name\":\"Example\",\"summary\":\"Runs a versioned code sample\","
                + "\"when_to_use\":\"User asks for the sample\",\"when_not_to_use\":\"Input unavailable\","
                + "\"parameters\":[{\"name\":\"count\",\"type\":\"integer\",\"required\":true,\"min\":1,\"max\":5}],"
                + "\"steps\":[{\"type\":\"code\",\"code_item_id\":\"9007199254740993\",\"code_version\":2,"
                + "\"entrypoint\":\"run\",\"args\":{\"count\":\"$count\"}}],"
                + "\"expected_results\":[\"JSON result\"],\"failure_policy\":\"stop\","
                + "\"examples\":[{\"input\":{\"count\":2},\"expected\":{\"ok\":true}}]}";
    }
}

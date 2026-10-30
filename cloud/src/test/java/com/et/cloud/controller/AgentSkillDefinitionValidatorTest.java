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

    @Test void comparesHighPrecisionBoundsWithoutRounding() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                validDefinition().replace("\"min\":1,\"max\":5", "\"min\":1.00000000000000001,\"max\":1"),
                (id, version, entrypoint) -> true));
        var result = validator.validate(validDefinition().replace("\"count\":\"$count\"",
                "\"count\":\"$count\",\"fraction\":1.00000000000000001,\"tiny\":1e-30,\"large\":123456789012345678901234567890"),
                (id, version, entrypoint) -> true);
        var args = result.path("steps").get(0).path("args");
        assertTrue(args.path("fraction").decimalValue().compareTo(new java.math.BigDecimal("1.00000000000000001")) == 0);
        assertTrue(args.path("tiny").decimalValue().compareTo(new java.math.BigDecimal("1e-30")) == 0);
        assertTrue(args.path("large").bigIntegerValue().toString().equals("123456789012345678901234567890"));
        assertDoesNotThrow(() -> validator.validate(
                validDefinition().replace("\"min\":1,\"max\":5", "\"min\":1,\"max\":1.00000000000000001"),
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

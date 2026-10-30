package com.et.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillDraftAuthoringTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void assemblesStructuredFieldsForCloud() throws Exception {
        String raw = "{\"definition\":{\"summary\":\"Replies\",\"name\":\"Greeting\","
                + "\"when_to_use\":\"Asked for a greeting\",\"when_not_to_use\":\"Otherwise\","
                + "\"parameters\":[],\"steps\":[{\"type\":\"response\",\"template\":\"Hello\"}],"
                + "\"expected_results\":[\"A greeting\"],\"failure_policy\":\"stop\","
                + "\"examples\":[{\"input\":{},\"expected\":\"Hello\"}]}}";
        JsonNode result = JSON.readTree(SkillDraftAuthoring.assemble(raw));
        assertEquals("Greeting", result.path("name").asText());
        assertEquals("response", result.path("steps").get(0).path("type").asText());
        assertFalse(result.has("definition"));
    }

    @Test void rejectsUnexpectedParameterFieldBeforeCloudWrite() {
        String raw = "{\"definition\":{\"parameters\":[{\"name\":\"x\",\"type\":\"string\","
                + "\"required\":true,\"description\":\"extra\"}]}}";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SkillDraftAuthoring.assemble(raw));
        assertTrue(error.getMessage().contains("definition.parameters[0].description"));
    }

    @Test void preservesPrecisionAndNormalizesOnlyDeclaredCodeVersion() throws Exception {
        String raw = "{\"definition\":{\"parameters\":[{\"name\":\"n\",\"type\":\"number\",\"required\":true,\"min\":1.00000000000000001,\"max\":1}],"
                + "\"steps\":[{\"type\":\"code\",\"code_version\":1.0,\"args\":{\"v\":1.00000000000000001}}],"
                + "\"examples\":[{\"input\":{\"v\":1.00000000000000001},\"expected\":{\"v\":1.00000000000000001}}]}}";
        String actual = SkillDraftAuthoring.assemble(raw);
        JsonNode node = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(actual);
        assertTrue(node.path("steps").get(0).path("code_version").isInt());
        assertEquals(new java.math.BigDecimal("1.00000000000000001"), node.path("parameters").get(0).path("min").decimalValue());
        assertEquals(new java.math.BigDecimal("1.00000000000000001"), node.path("steps").get(0).path("args").path("v").decimalValue());
        assertEquals(new java.math.BigDecimal("1.00000000000000001"), node.path("examples").get(0).path("expected").path("v").decimalValue());
    }
}

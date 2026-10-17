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
}

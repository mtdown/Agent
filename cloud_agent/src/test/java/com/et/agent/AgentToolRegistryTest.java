package com.et.agent;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolRegistryTest {
    @Test void eachModelToolHasAnExecutableHandler() {
        Map<String, AgentLoop.Tool> tools = AgentToolRegistry.bind("7", mock(AgentLibraryGateway.class));
        assertEquals(AgentToolRegistry.names(), tools.keySet());
        for (String name : tools.keySet()) {
            assertEquals(name, AgentToolRegistry.declaration(name).path("function").path("name").asText());
            assertEquals("object", AgentToolRegistry.declaration(name).path("function")
                    .path("parameters").path("type").asText());
        }
        assertTrue(tools.containsKey("get_skill"));
        assertTrue(tools.containsKey("create_skill"));
        assertTrue(tools.containsKey("create_code"));
        assertTrue(tools.containsKey("write_file"));
        assertTrue(tools.containsKey("read_file"));
        assertTrue(tools.containsKey("write_wiki_document"));
    }

    @Test void everyDeclarationHasExplicitDomainAndAccess() {
        Map<String, AgentToolRegistry.Domain> domains = Map.ofEntries(
                Map.entry("get_skill", AgentToolRegistry.Domain.SKILL), Map.entry("create_skill", AgentToolRegistry.Domain.SKILL),
                Map.entry("create_code", AgentToolRegistry.Domain.CODE), Map.entry("write_file", AgentToolRegistry.Domain.FILE),
                Map.entry("read_file", AgentToolRegistry.Domain.FILE), Map.entry("read_file_segment", AgentToolRegistry.Domain.FILE),
                Map.entry("search_file", AgentToolRegistry.Domain.FILE), Map.entry("write_wiki_document", AgentToolRegistry.Domain.WIKI),
                Map.entry("create_wiki_folder", AgentToolRegistry.Domain.WIKI), Map.entry("create_wiki_document", AgentToolRegistry.Domain.WIKI),
                Map.entry("list_writable_wiki_locations", AgentToolRegistry.Domain.WIKI), Map.entry("plan_wiki_destination", AgentToolRegistry.Domain.WIKI));
        java.util.Set<String> writes = java.util.Set.of("create_skill", "create_code", "write_file", "write_wiki_document", "create_wiki_folder", "create_wiki_document");
        assertEquals(domains.keySet(), AgentToolRegistry.names());
        domains.forEach((name, domain) -> {
            assertEquals(domain, AgentToolRegistry.domain(name), name);
            assertEquals(writes.contains(name) ? AgentToolRegistry.Access.WRITE : AgentToolRegistry.Access.READ,
                    AgentToolRegistry.access(name), name);
        });
        assertThrows(IllegalArgumentException.class, () -> AgentToolRegistry.access("unregistered"));
    }

    @Test void unknownToolCannotBeDeclared() {
        assertThrows(IllegalArgumentException.class, () -> AgentToolRegistry.declaration("shell"));
    }

    @Test void createdCodeReportsPublishedVersionWithoutClaimingValidation() throws Exception {
        AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
        when(gateway.createCode(org.mockito.ArgumentMatchers.eq("7"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("9007199254740993");
        String result = AgentToolRegistry.bind("7", gateway).get("create_code").execute(
                "{\"name\":\"Example\",\"summary\":\"Example\",\"language\":\"PYTHON\","
                        + "\"entrypoint\":\"run\",\"sourceCode\":\"def run(inputs, resources): return {}\","
                        + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}},"
                        + "\"outputSchema\":{\"type\":\"object\",\"properties\":{}}}");
        com.fasterxml.jackson.databind.JsonNode saved = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
        assertEquals("9007199254740993", saved.path("id").asText());
        assertEquals(1, saved.path("version").asInt());
        assertTrue(saved.path("published").asBoolean());
        assertEquals("UNVERIFIED", saved.path("validationState").asText());
    }

    @Test void createdSkillReportsActiveVersion() throws Exception {
        AgentLibraryGateway gateway = mock(AgentLibraryGateway.class);
        when(gateway.createSkill(org.mockito.ArgumentMatchers.eq("7"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("9007199254740993");
        String result = AgentToolRegistry.bind("7", gateway).get("create_skill").execute(
                "{\"definition\":{\"name\":\"Greeting\",\"summary\":\"Replies politely\","
                        + "\"when_to_use\":\"Asked to greet\",\"when_not_to_use\":\"Otherwise\","
                        + "\"parameters\":[],\"steps\":[{\"type\":\"response\",\"template\":\"Hello\"}],"
                        + "\"expected_results\":[\"Hello\"],\"failure_policy\":\"stop\","
                        + "\"examples\":[{\"input\":{},\"expected\":\"Hello\"}]}}");
        com.fasterxml.jackson.databind.JsonNode saved = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
        assertEquals("9007199254740993", saved.path("id").asText());
        assertEquals("ACTIVE", saved.path("state").asText());
        assertEquals(1, saved.path("version").asInt());
    }
}

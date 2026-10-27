package com.et.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentWikiDestinationToolTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void unspecifiedDestinationRequestsAllWritableChoicesWithoutSelectingPersonal() throws Exception {
        AgentLoop.Tool tool = AgentToolRegistry.bind("7", new DirectoryGateway()).get("plan_wiki_destination");
        assertNotNull(tool);
        JsonNode result = JSON.readTree(tool.execute("{\"destinationType\":\"unspecified\"}"));
        assertTrue(result.path("choiceRequired").asBoolean());
        assertEquals(5, result.path("candidates").size());
        assertEquals("11", result.path("recommendedSpaceId").asText());
    }

    @Test void exactTeamMatchProceedsButDuplicateTeamNamesRequireChoice() throws Exception {
        AgentLoop.Tool tool = AgentToolRegistry.bind("7", new DirectoryGateway()).get("plan_wiki_destination");
        JsonNode unique = JSON.readTree(tool.execute(
                "{\"destinationType\":\"team\",\"teamName\":\"产品部\"}"));
        assertFalse(unique.path("choiceRequired").asBoolean());
        assertEquals("21", unique.path("spaceId").asText());
        JsonNode duplicate = JSON.readTree(tool.execute(
                "{\"destinationType\":\"team\",\"teamName\":\"同名组\"}"));
        assertTrue(duplicate.path("choiceRequired").asBoolean());
        assertEquals(2, duplicate.path("candidates").size());
        assertThrows(IllegalArgumentException.class, () -> tool.execute(
                "{\"destinationType\":\"team\",\"teamName\":\"未知\"}"));
    }

    private static final class DirectoryGateway implements AgentLibraryGateway {
        @Override public String listWritableWikiLocations(String user, String space, String cursor, Integer limit) {
            assertEquals("7", user);
            assertNull(space);
            return "{\"defaultPersonalSpaceId\":\"11\",\"spaces\":["
                    + "{\"id\":\"11\",\"name\":\"个人区-7\",\"type\":\"personal\"},"
                    + "{\"id\":\"21\",\"name\":\"产品部\",\"type\":\"team\"},"
                    + "{\"id\":\"31\",\"name\":\"同名组\",\"type\":\"team\"},"
                    + "{\"id\":\"32\",\"name\":\"同名组\",\"type\":\"team\"},"
                    + "{\"id\":\"41\",\"name\":\"公开文档\",\"type\":\"public\"}]}";
        }
        @Override public String getSkill(String user, String id, int version) { throw new UnsupportedOperationException(); }
        @Override public String createSkill(String user, String definition) { throw new UnsupportedOperationException(); }
        @Override public String createCode(String user, String definition) { throw new UnsupportedOperationException(); }
        @Override public String writeFile(String user, String path, String content, String mode) { throw new UnsupportedOperationException(); }
        @Override public String readFile(String user, String path) { throw new UnsupportedOperationException(); }
        @Override public String writeWikiDocument(String user, String space, String title, String content) {
            throw new UnsupportedOperationException();
        }
    }
}

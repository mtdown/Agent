package com.et.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentWikiCreationToolsTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void declaresAndBindsBothCreationToolsToCurrentUser() throws Exception {
        RecordingGateway gateway = new RecordingGateway();
        Map<String, AgentLoop.Tool> tools = AgentToolRegistry.bind("9007199254740993", gateway);
        assertTrue(tools.containsKey("create_wiki_folder"));
        assertTrue(tools.containsKey("create_wiki_document"));
        JsonNode folder = JSON.readTree(tools.get("create_wiki_folder").execute(
                "{\"spaceId\":\"88\",\"taskName\":\"整理资料\"}"));
        assertEquals("9007199254740993:88:整理资料", gateway.lastCall);
        assertEquals("9007199254740995", folder.path("folderId").asText());
        JsonNode document = JSON.readTree(tools.get("create_wiki_document").execute(
                "{\"spaceId\":\"88\",\"folderId\":\"9007199254740995\","
                        + "\"title\":\"标题\",\"content\":\"正文\"}"));
        assertEquals("9007199254740993:88:9007199254740995:标题:正文", gateway.lastCall);
        assertEquals("9007199254740997", document.path("documentId").asText());
        assertFalse(AgentToolRegistry.declaration("create_wiki_folder").path("function")
                .path("parameters").path("properties").has("userId"));
        assertFalse(AgentToolRegistry.declaration("create_wiki_document").path("function")
                .path("parameters").path("properties").has("userId"));
        assertThrows(IllegalArgumentException.class,
                () -> tools.get("create_wiki_document").execute("{\"spaceId\":\"88\",\"content\":\"正文\"}"));
    }

    @Test void legacyToolReturnsRealLocationIds() throws Exception {
        RecordingGateway gateway = new RecordingGateway();
        String result = AgentToolRegistry.bind("7", gateway).get("write_wiki_document").execute(
                "{\"spaceId\":\"88\",\"title\":\"标题\",\"content\":\"正文\"}");
        JsonNode parsed = JSON.readTree(result);
        assertEquals("9007199254740995", parsed.path("folderId").asText());
        assertEquals("9007199254740997", parsed.path("documentId").asText());
        assertEquals("7:88:标题:正文", gateway.lastCall);
    }

    @Test void selectedDestinationCannotBeChangedByModelToolArguments() throws Exception {
        RecordingGateway gateway = new RecordingGateway();
        Map<String, AgentLoop.Tool> tools = AgentToolRegistry.bind("7", gateway, "88");
        assertThrows(IllegalArgumentException.class, () -> tools.get("create_wiki_folder").execute(
                "{\"spaceId\":\"89\",\"taskName\":\"错误目标\"}"));
        assertThrows(IllegalArgumentException.class, () -> tools.get("create_wiki_document").execute(
                "{\"spaceId\":\"89\",\"folderId\":\"1\",\"content\":\"正文\"}"));
        assertThrows(IllegalArgumentException.class, () -> tools.get("write_wiki_document").execute(
                "{\"spaceId\":\"89\",\"title\":\"标题\",\"content\":\"正文\"}"));
        assertNull(gateway.lastCall);
        tools.get("create_wiki_folder").execute("{\"spaceId\":\"88\",\"taskName\":\"正确目标\"}");
        assertEquals("7:88:正确目标", gateway.lastCall);
    }

    private static final class RecordingGateway implements AgentLibraryGateway {
        String lastCall;

        @Override public String getSkill(String userId, String id, int version) { throw new UnsupportedOperationException(); }
        @Override public String createSkill(String userId, String definition) { throw new UnsupportedOperationException(); }
        @Override public String createCode(String userId, String definition) { throw new UnsupportedOperationException(); }
        @Override public String writeFile(String userId, String path, String content, String mode) { throw new UnsupportedOperationException(); }
        @Override public String readFile(String userId, String path) { throw new UnsupportedOperationException(); }

        @Override public String writeWikiDocument(String userId, String spaceId, String title, String content) {
            lastCall = userId + ":" + spaceId + ":" + title + ":" + content;
            return "{\"spaceId\":\"88\",\"folderId\":\"9007199254740995\","
                    + "\"documentId\":\"9007199254740997\",\"title\":\"标题\"}";
        }

        @Override public String createWikiFolder(String userId, String spaceId, String taskName) {
            lastCall = userId + ":" + spaceId + ":" + taskName;
            return "{\"spaceId\":\"88\",\"folderId\":\"9007199254740995\",\"name\":\"整理资料\"}";
        }

        @Override public String createWikiDocument(String userId, String spaceId, String folderId,
                                                   String title, String content) {
            lastCall = userId + ":" + spaceId + ":" + folderId + ":" + title + ":" + content;
            return "{\"spaceId\":\"88\",\"folderId\":\"9007199254740995\","
                    + "\"documentId\":\"9007199254740997\",\"title\":\"标题\"}";
        }
    }
}

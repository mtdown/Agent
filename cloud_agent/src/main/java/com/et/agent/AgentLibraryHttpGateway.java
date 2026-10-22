package com.et.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** The Agent never opens host paths; Cloud owns user-scoped Skill and code records. */
@Component
public final class AgentLibraryHttpGateway implements AgentLibraryGateway {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private final String key;

    public AgentLibraryHttpGateway(@Value("${agent.cloud-base-url:http://127.0.0.1:8123}") String baseUrl,
                                   @Value("${agent.internal-key}") String key) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.key = key;
    }

    @Override public String getSkill(String userId, String id, int version) {
        if (!id.matches("[1-9][0-9]{0,18}") || version < 1) throw new IllegalArgumentException("Invalid Skill reference");
        return call("/api/internal/agent/skills/" + id + "/versions/" + version, userId, null).toString();
    }

    @Override public String createSkill(String userId, String definition) {
        return call("/api/internal/agent/skills", userId, definition).asText();
    }

    @Override public String createCode(String userId, String definition) {
        return call("/api/internal/agent/codes", userId, definition).asText();
    }

    @Override public String writeFile(String userId, String path, String content, String mode) {
        ObjectNode body = JSON.createObjectNode();
        body.put("path", path); body.put("content", content); body.put("mode", mode);
        return call("/api/internal/agent/files", userId, body.toString()).toString();
    }

    @Override public String readFile(String userId, String path) {
        try {
            return call("/api/internal/agent/files?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8.name()),
                    userId, null).toString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is unavailable", e);
        }
    }

    @Override public String writeWikiDocument(String userId, String spaceId, String title, String content) {
        ObjectNode body = JSON.createObjectNode();
        body.put("spaceId", spaceId); body.put("title", title); body.put("content", content);
        return call("/api/internal/agent/wiki-documents", userId, body.toString()).asText();
    }

    @Override public String listWritableWikiLocations(String userId, String spaceId, String cursor, Integer limit) {
        if (spaceId != null && !spaceId.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid spaceId");
        if (cursor != null && !cursor.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid cursor");
        if (cursor != null && spaceId == null) throw new IllegalArgumentException("cursor requires spaceId");
        if (limit != null && (limit < 1 || limit > 100)) throw new IllegalArgumentException("Invalid limit");
        StringBuilder path = new StringBuilder("/api/internal/agent/wiki-locations");
        if (spaceId != null) path.append("?spaceId=").append(spaceId);
        if (cursor != null) path.append(spaceId == null ? '?' : '&').append("cursor=").append(cursor);
        if (limit != null) path.append(path.indexOf("?") < 0 ? '?' : '&').append("limit=").append(limit);
        return call(path.toString(), userId, null).toString();
    }

    private JsonNode call(String path, String userId, String body) {
        if (key == null || key.isEmpty()) throw new IllegalStateException("Agent service credential is not configured");
        if (userId == null || !userId.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid user context");
        try {
            URI uri = URI.create(baseUrl + path + (path.contains("?") ? "&" : "?")
                    + "userId=" + URLEncoder.encode(userId, StandardCharsets.UTF_8.name()));
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("X-Agent-Key", key).header("Accept", "application/json");
            HttpRequest request = (body == null ? builder.GET() : builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IllegalStateException("Cloud library HTTP " + response.statusCode());
            JsonNode envelope = JSON.readTree(response.body());
            if (envelope.path("code").asInt(-1) != 0 || envelope.get("data") == null || envelope.get("data").isNull())
                throw new IllegalStateException("Cloud library rejected operation: " + envelope.path("message").asText("unknown"));
            return envelope.get("data");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentLoop.Cancelled();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cloud library request failed", e);
        }
    }
}

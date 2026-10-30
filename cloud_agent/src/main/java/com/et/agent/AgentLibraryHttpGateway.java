package com.et.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final Duration requestTimeout;

    static final class CloudFailure extends IllegalStateException {
        final int code;
        final boolean unknown;
        CloudFailure(int code, String message, boolean unknown) {
            super(message);
            this.code = code;
            this.unknown = unknown;
        }
    }

    @Autowired
    public AgentLibraryHttpGateway(@Value("${agent.cloud-base-url:http://127.0.0.1:8123}") String baseUrl,
                                   @Value("${agent.internal-key}") String key) {
        this(baseUrl, key, Duration.ofSeconds(20));
    }

    AgentLibraryHttpGateway(String baseUrl, String key, Duration requestTimeout) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.key = key;
        this.requestTimeout = requestTimeout;
    }

    @Override public String getSkill(String userId, String id, int version) {
        if (!(id.matches("[1-9][0-9]{0,18}") || "builtin:wiki-write".equals(id)) || version < 1)
            throw new IllegalArgumentException("Invalid Skill reference");
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

    @Override public String readFileSegment(String userId, String path, String cursor) {
        return fileQuery("/api/internal/agent/files/segment", userId, path, null, cursor);
    }
    @Override public String searchFile(String userId, String path, String query, String cursor) {
        return fileQuery("/api/internal/agent/files/search", userId, path, query, cursor);
    }
    private String fileQuery(String endpoint, String user, String path, String query, String cursor) {
        try {
            String url = endpoint + "?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8.name());
            if (query != null) url += "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            if (cursor != null) url += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8.name());
            return call(url, user, null).toString();
        } catch (java.io.UnsupportedEncodingException e) { throw new IllegalStateException("UTF-8 is unavailable", e); }
    }

    @Override public String writeWikiDocument(String userId, String spaceId, String title, String content) {
        ObjectNode body = JSON.createObjectNode();
        body.put("spaceId", spaceId); body.put("title", title); body.put("content", content);
        return call("/api/internal/agent/wiki-documents", userId, body.toString()).toString();
    }

    @Override public String createWikiFolder(String userId, String spaceId, String taskName) {
        ObjectNode body = JSON.createObjectNode();
        body.put("spaceId", spaceId); body.put("taskName", taskName);
        return call("/api/internal/agent/wiki-folders", userId, body.toString()).toString();
    }

    @Override public String createWikiDocument(String userId, String spaceId, String folderId,
                                               String title, String content) {
        ObjectNode body = JSON.createObjectNode();
        body.put("spaceId", spaceId); body.put("folderId", folderId);
        if (title != null) body.put("title", title);
        body.put("content", content);
        return call("/api/internal/agent/wiki-documents/create", userId, body.toString()).toString();
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

    private static String safeMessage(int code, String message) {
        // Only fixed, reviewed messages can cross the service boundary verbatim.
        if (java.util.Arrays.asList("请求被拒绝", "Invalid code entrypoint", "Invalid personal file path",
                "File content exceeds 1 MiB", "Invalid file content or write mode", "Symbolic links are not allowed",
                "Personal file version conflict", "Code version not found", "Skill version not found", "Item not found or not owned",
                "Invalid space or cursor ID", "cursor requires spaceId", "limit must be 1..100").contains(message)) return message;
        switch (code) {
            case 40000: return "Invalid request";
            case 40100: return "Authentication required";
            case 40101: case 40300: return "Permission denied";
            case 40400: return "Requested item not found";
            default: return "Cloud operation failed";
        }
    }

    private JsonNode call(String path, String userId, String body) {
        if (key == null || key.isEmpty()) throw new IllegalStateException("Agent service credential is not configured");
        if (userId == null || !userId.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid user context");
        try {
            URI uri = URI.create(baseUrl + path + (path.contains("?") ? "&" : "?")
                    + "userId=" + URLEncoder.encode(userId, StandardCharsets.UTF_8.name()));
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(requestTimeout)
                    .header("X-Agent-Key", key).header("Accept", "application/json");
            HttpRequest request = (body == null ? builder.GET() : builder.header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw uncertain(body);
            JsonNode envelope = JSON.readTree(response.body());
            if (envelope == null || !envelope.isObject() || !envelope.path("code").isIntegralNumber()
                    || !envelope.path("code").canConvertToInt()) throw uncertain(body);
            int code = envelope.path("code").asInt();
            if (code != 0) {
                if (body != null && code != 40000 && code != 40100 && code != 40101 && code != 40300 && code != 40400)
                    throw uncertain(body);
                throw new CloudFailure(code, safeMessage(code, envelope.path("message").asText()), false);
            }
            if (envelope.get("data") == null || envelope.get("data").isNull()) throw uncertain(body);
            return envelope.get("data");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentLoop.Cancelled();
        } catch (CloudFailure e) {
            throw e;
        } catch (Exception e) {
            throw uncertain(body);
        }
    }

    private static CloudFailure uncertain(String body) {
        return new CloudFailure(50001, body == null ? "Cloud read failed" : "Cloud write result is unknown", body != null);
    }
}

package com.et.cloud.controller;

import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.WikiSpaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps Wiki destination IDs server-side while the browser uses one-use choice tokens. */
@Service
public class AgentWikiChoiceService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long TTL_MILLIS = 30L * 60L * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final AgentWritableWikiLocations directory;
    private final WikiSpaceService spaces;
    private final DocumentWikiService documents;

    public AgentWikiChoiceService(DataSource dataSource, AgentWritableWikiLocations directory,
                                  WikiSpaceService spaces, DocumentWikiService documents) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.directory = directory;
        this.spaces = spaces;
        this.documents = documents;
    }

    public static final class Prepared {
        public final Map<String, Object> display;
        public final String internalJson;
        Prepared(Map<String, Object> display, String internalJson) {
            this.display = display;
            this.internalJson = internalJson;
        }
    }

    public static final class Selection {
        public final String originalQuery, spaceId, label;
        Selection(String originalQuery, String spaceId, String label) {
            this.originalQuery = originalQuery;
            this.spaceId = spaceId;
            this.label = label;
        }
    }

    public Prepared prepare(User owner, long conversationId, long turnId, JsonNode request) {
        if (owner == null || owner.getId() == null || request == null ||
                !request.path("choiceRequired").asBoolean(false) || !request.path("candidates").isArray())
            throw new IllegalArgumentException("Invalid Wiki destination choice");
        Map<String, Object> fresh = directory.list(owner.getId(), null, null, null);
        Map<String, Map<?, ?>> writable = new HashMap<>();
        for (Object raw : (List<?>) fresh.get("spaces")) {
            Map<?, ?> space = (Map<?, ?>) raw;
            writable.put(String.valueOf(space.get("id")), space);
        }
        ObjectNode internal = JSON.createObjectNode();
        internal.put("kind", "destination_choice");
        internal.put("ownerId", owner.getId().toString());
        internal.put("conversationId", Long.toString(conversationId));
        internal.put("turnId", Long.toString(turnId));
        long expiresAt = Instant.now().toEpochMilli() + TTL_MILLIS;
        internal.put("expiresAt", expiresAt);
        ArrayNode storedOptions = internal.putArray("options");
        List<Map<String, Object>> displayOptions = new ArrayList<>();
        Map<String, Integer> labelCounts = new HashMap<>();
        for (JsonNode candidate : request.path("candidates")) {
            String id = candidate.path("id").asText("");
            Map<?, ?> space = writable.remove(id);
            if (space == null) continue;
            String label = displayLabel(String.valueOf(space.get("type")), String.valueOf(space.get("name")));
            int count = labelCounts.merge(label, 1, Integer::sum);
            if (count > 1) label += "（选项 " + count + "）";
            byte[] random = new byte[32];
            RANDOM.nextBytes(random);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            boolean recommended = id.equals(request.path("recommendedSpaceId").asText(""));
            storedOptions.addObject().put("token", token).put("spaceId", id)
                    .put("label", label).put("recommended", recommended);
            Map<String, Object> shown = new LinkedHashMap<>();
            shown.put("token", token);
            shown.put("label", label);
            shown.put("recommended", recommended);
            displayOptions.add(shown);
            if (displayOptions.size() >= 50) break;
        }
        if (displayOptions.isEmpty()) throw new IllegalArgumentException("No writable destination is available");
        Map<String, Object> display = new LinkedHashMap<>();
        display.put("kind", "destination_choice");
        display.put("turnId", Long.toString(turnId));
        display.put("expiresAt", expiresAt);
        display.put("options", displayOptions);
        return new Prepared(display, internal.toString());
    }

    @Transactional
    public Selection claim(User owner, long conversationId, long turnId, String token) {
        if (owner == null || owner.getId() == null || token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("Invalid or expired choice");
        List<Integer> conversations = jdbc.query("SELECT busy FROM agent_conversation WHERE id=? " +
                        "AND ownerUserId=? AND isDelete=0 FOR UPDATE", (rs, row) -> rs.getInt(1),
                conversationId, owner.getId());
        if (conversations.size() != 1 || conversations.get(0) != 0)
            throw new IllegalArgumentException("Conversation is unavailable");
        Long latestUserTurn = jdbc.queryForObject("SELECT MAX(id) FROM agent_message WHERE conversationId=? " +
                "AND role='user'", Long.class, conversationId);
        if (latestUserTurn == null || latestUserTurn != turnId)
            throw new IllegalArgumentException("Choice is no longer current");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, interactionJson, choiceConsumed " +
                "FROM agent_message WHERE conversationId=? AND turnId=? AND role='assistant' " +
                "AND status='DONE' AND interactionJson IS NOT NULL", conversationId, turnId);
        if (rows.size() != 1 || ((Number) rows.get(0).get("choiceConsumed")).intValue() != 0)
            throw new IllegalArgumentException("Invalid or expired choice");
        final JsonNode internal;
        try { internal = JSON.readTree(String.valueOf(rows.get(0).get("interactionJson"))); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid choice state", e); }
        if (!"destination_choice".equals(internal.path("kind").asText()) ||
                !owner.getId().toString().equals(internal.path("ownerId").asText()) ||
                !Long.toString(conversationId).equals(internal.path("conversationId").asText()) ||
                !Long.toString(turnId).equals(internal.path("turnId").asText()) ||
                internal.path("expiresAt").asLong(0) <= Instant.now().toEpochMilli())
            throw new IllegalArgumentException("Invalid or expired choice");
        JsonNode selected = null;
        for (JsonNode option : internal.path("options")) {
            if (MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                    option.path("token").asText("").getBytes(StandardCharsets.UTF_8))) {
                selected = option;
                break;
            }
        }
        if (selected == null) throw new IllegalArgumentException("Invalid or expired choice");
        String spaceId = selected.path("spaceId").asText();
        spaces.requireSpaceAccess(Long.parseLong(spaceId), owner, true);
        if (jdbc.update("UPDATE agent_message SET choiceConsumed=1 WHERE id=? AND choiceConsumed=0",
                rows.get(0).get("id")) != 1)
            throw new IllegalArgumentException("Choice was already used");
        String original = jdbc.queryForObject("SELECT content FROM agent_message WHERE id=? " +
                "AND conversationId=? AND role='user' AND status='DONE'", String.class,
                turnId, conversationId);
        return new Selection(original, spaceId, selected.path("label").asText());
    }

    public static String displayLabel(String type, String name) {
        if ("personal".equals(type)) return "个人空间";
        if ("public".equals(type)) return "公开空间";
        if ("team".equals(type)) return "团队「" + name + "」";
        throw new IllegalArgumentException("Unknown Wiki space type");
    }

    /** Resolve a fresh, permission-checked label without exposing the storage name. */
    public String displayLabelFor(User owner, String spaceId) {
        if (owner == null || owner.getId() == null || spaceId == null) throw new IllegalArgumentException("Invalid space");
        Map<String, Object> fresh = directory.list(owner.getId(), null, null, null);
        for (Object raw : (List<?>) fresh.get("spaces")) {
            Map<?, ?> space = (Map<?, ?>) raw;
            if (spaceId.equals(String.valueOf(space.get("id"))))
                return displayLabel(String.valueOf(space.get("type")), String.valueOf(space.get("name")));
        }
        throw new IllegalArgumentException("Wiki destination is no longer writable");
    }

    public static Map<String, Object> safeDisplay(String internalJson, boolean consumed) {
        try {
            JsonNode state = JSON.readTree(internalJson);
            Map<String, Object> display = new LinkedHashMap<>();
            display.put("kind", state.path("kind").asText());
            display.put("turnId", state.path("turnId").asText());
            if ("wiki_write_result".equals(state.path("kind").asText())) {
                display.put("spaceLabel", state.path("spaceLabel").asText());
                display.put("folderName", state.path("folderName").asText(""));
                display.put("documentTitle", state.path("documentTitle").asText());
                display.put("openToken", state.path("openToken").asText());
                return display;
            }
            if ("wiki_partial_result".equals(state.path("kind").asText())) {
                display.put("spaceLabel", state.path("spaceLabel").asText());
                display.put("folderName", state.path("folderName").asText());
                return display;
            }
            display.put("expiresAt", state.path("expiresAt").asLong());
            display.put("consumed", consumed);
            List<Map<String, Object>> options = new ArrayList<>();
            for (JsonNode option : state.path("options")) {
                Map<String, Object> shown = new LinkedHashMap<>();
                shown.put("token", option.path("token").asText());
                shown.put("label", option.path("label").asText());
                shown.put("recommended", option.path("recommended").asBoolean());
                options.add(shown);
            }
            display.put("options", options);
            return display;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid stored Agent interaction", e);
        }
    }

    public Map<String, String> preview(User owner, long conversationId, long turnId, String openToken) {
        if (owner == null || owner.getId() == null || openToken == null ||
                !openToken.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Invalid document action");
        List<String> states = jdbc.query("SELECT m.interactionJson FROM agent_message m " +
                        "JOIN agent_conversation c ON c.id=m.conversationId " +
                        "WHERE m.conversationId=? AND m.turnId=? AND m.role='assistant' " +
                        "AND m.status='DONE' AND c.ownerUserId=? AND c.isDelete=0 AND m.interactionJson IS NOT NULL",
                (rs, row) -> rs.getString(1), conversationId, turnId, owner.getId());
        if (states.size() != 1) throw new IllegalArgumentException("Document action not found");
        final JsonNode state;
        try { state = JSON.readTree(states.get(0)); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid document action", e); }
        if (!"wiki_write_result".equals(state.path("kind").asText()) ||
                !owner.getId().toString().equals(state.path("ownerId").asText()) ||
                !Long.toString(conversationId).equals(state.path("conversationId").asText()) ||
                !Long.toString(turnId).equals(state.path("turnId").asText()) ||
                !MessageDigest.isEqual(openToken.getBytes(StandardCharsets.UTF_8),
                        state.path("openToken").asText("").getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("Invalid document action");
        long documentId = Long.parseLong(state.path("documentId").asText());
        long spaceId = Long.parseLong(state.path("spaceId").asText());
        spaces.requireSpaceAccess(spaceId, owner, false);
        DocumentWiki document = documents.getById(documentId);
        if (document == null || !Long.valueOf(spaceId).equals(document.getSpaceId()) ||
                !Integer.valueOf(0).equals(document.getIsDelete()))
            throw new IllegalArgumentException("Document is unavailable");
        Map<String, String> shown = new LinkedHashMap<>();
        shown.put("title", document.getTitle());
        shown.put("content", document.getContent());
        shown.put("contentFormat", document.getContentFormat());
        return shown;
    }
}

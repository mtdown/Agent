package com.et.cloud.controller;

import com.et.cloud.model.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Keeps each confirmed Wiki operation separate for one conversation turn. */
public final class AgentWikiChoiceProjection implements AgentPreviewProxy.WikiEvents {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AgentWikiChoiceService choices;
    private final User owner;
    private final long conversationId, turnId;
    private String choiceJson;
    private final List<ObjectNode> documents = new ArrayList<>();
    private final List<ObjectNode> folders = new ArrayList<>();
    private final List<ObjectNode> operations = new ArrayList<>();
    private int unknown, failed;
    private boolean attempted;

    public AgentWikiChoiceProjection(AgentWikiChoiceService choices, User owner, long conversationId, long turnId) {
        this.choices = choices; this.owner = owner; this.conversationId = conversationId; this.turnId = turnId;
    }
    @Override public AgentPreviewProxy.PublicEvent project(String event, JsonNode data) {
        if ("wiki_choice_request".equals(event)) {
            if (choiceJson != null) throw new IllegalStateException("Duplicate Wiki destination choice");
            AgentWikiChoiceService.Prepared prepared = choices.prepare(owner, conversationId, turnId, data);
            choiceJson = prepared.internalJson;
            return new AgentPreviewProxy.PublicEvent("wiki_choice", prepared.display);
        }
        if (!"wiki_tool_event".equals(event)) return null;
        String name = data.path("name").asText("");
        if (!"create_wiki_folder".equals(name) && !"create_wiki_document".equals(name) && !"write_wiki_document".equals(name)) return null;
        attempted = true;
        JsonNode result = data.path("result");
        ObjectNode operation = JSON.createObjectNode(); operation.put("tool",name); operations.add(operation);
        if ("unknown".equals(result.path("status").asText())) { operation.put("status","unknown"); unknown++; return null; }
        if ("blocked".equals(result.path("status").asText())) { operation.put("status","blocked"); failed++; return null; }
        if (result.path("saved").isBoolean() && !result.path("saved").asBoolean()) { operation.put("status","failed"); failed++; return null; }
        operation.put("status","unavailable");
        if ("create_wiki_folder".equals(name)) {
            String space = result.path("spaceId").asText(""); String folder = result.path("folderId").asText("");
            if (space.matches("[1-9][0-9]{0,18}") && folder.matches("[1-9][0-9]{0,18}")) {
                ObjectNode item = JSON.createObjectNode(); item.put("spaceId", space); item.put("folderId", folder);
                item.put("folderName", result.path("name").asText("")); item.put("spaceLabel", safeLabel(space)); folders.add(item);
                operation.put("status","success"); operation.put("folderId",folder);
            }
        } else {
            String id = result.path("documentId").asText("");
            if (id.matches("[1-9][0-9]{0,18}")) {
                ObjectNode item = JSON.createObjectNode(); item.put("documentId", id);
                item.put("spaceId", result.path("spaceId").asText()); item.put("folderId", result.path("folderId").asText());
                item.put("documentTitle", result.path("title").asText()); item.put("folderName", result.path("folderName").asText());
                item.put("spaceLabel", safeLabel(result.path("spaceId").asText()));
                byte[] random = new byte[32]; RANDOM.nextBytes(random);
                item.put("openToken", Base64.getUrlEncoder().withoutPadding().encodeToString(random)); documents.add(item);
                operation.put("status","success"); operation.put("documentId",id);
            }
        }
        return null;
    }
    private String safeLabel(String space) {
        try { return choices.displayLabelFor(owner, space); } catch (RuntimeException ignored) { return ""; }
    }
    private ObjectNode state() {
        ObjectNode state = JSON.createObjectNode();
        boolean singlePair = folders.isEmpty() || (folders.size() == 1 && documents.size() == 1 &&
                folders.get(0).path("folderId").asText().equals(documents.get(0).path("folderId").asText()));
        String kind = documents.size() == 1 && singlePair && (folders.isEmpty() || (unknown == 0 && failed == 0)) ? "wiki_write_result" :
                documents.isEmpty() && folders.size() == 1 && unknown == 0 && failed == 1 && operations.size() == 2 && "create_wiki_document".equals(operations.get(1).path("tool").asText()) && "failed".equals(operations.get(1).path("status").asText()) ? "wiki_partial_result" : "wiki_write_results";
        state.put("kind", kind); state.put("ownerId", owner.getId().toString());
        state.put("conversationId", Long.toString(conversationId)); state.put("turnId", Long.toString(turnId));
        if ("wiki_write_result".equals(kind)) {
            ObjectNode doc = documents.get(0);
            doc.fields().forEachRemaining(e -> state.set(e.getKey(), e.getValue()));
            if (doc.path("folderName").asText().isEmpty() && !folders.isEmpty()) state.put("folderName", folders.get(0).path("folderName").asText());
        } else if ("wiki_partial_result".equals(kind)) {
            folders.get(0).fields().forEachRemaining(e -> state.set(e.getKey(), e.getValue()));
        }
        ArrayNode ds = state.putArray("documents"); documents.forEach(ds::add);
        ArrayNode fs = state.putArray("folders"); folders.forEach(fs::add);
        ArrayNode ops = state.putArray("operations"); operations.forEach(ops::add);
        state.put("unknown", unknown); state.put("failed", failed);
        return state;
    }
    @Override public String interactionJson() {
        if (choiceJson != null && !attempted) return choiceJson;
        return attempted ? state().toString() : null;
    }
    @Override public Map<String, Object> displayInteraction(String internal) { return choices.currentDisplay(owner, internal, false); }
    @Override public String displayAnswer() {
        if (choiceJson != null && !attempted) return "请选择写入空间";
        if (!attempted) return null;
        Map<String,Object> display = choices.currentDisplay(owner, state().toString(), false);
        return AgentWikiChoiceService.answerFor(display, unknown, failed);
    }
}

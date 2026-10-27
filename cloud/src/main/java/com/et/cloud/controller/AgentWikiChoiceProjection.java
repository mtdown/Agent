package com.et.cloud.controller;

import com.et.cloud.model.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.SecureRandom;
import java.util.Base64;

/** Converts internal Agent Wiki events to safe browser events for one turn. */
public final class AgentWikiChoiceProjection implements AgentPreviewProxy.WikiEvents {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AgentWikiChoiceService choices;
    private final User owner;
    private final long conversationId, turnId;
    private String interactionJson;
    private String interactionKind;
    private String spaceId, spaceLabel, folderId, folderName, documentTitle;
    private boolean wikiWriteAttempted;

    public AgentWikiChoiceProjection(AgentWikiChoiceService choices, User owner, long conversationId, long turnId) {
        this.choices = choices;
        this.owner = owner;
        this.conversationId = conversationId;
        this.turnId = turnId;
    }

    @Override public AgentPreviewProxy.PublicEvent project(String event, JsonNode data) {
        if ("wiki_choice_request".equals(event)) {
            if (interactionJson != null) throw new IllegalStateException("Duplicate Wiki destination choice");
            AgentWikiChoiceService.Prepared prepared = choices.prepare(owner, conversationId, turnId, data);
            interactionJson = prepared.internalJson;
            interactionKind = "destination_choice";
            return new AgentPreviewProxy.PublicEvent("wiki_choice", prepared.display);
        }
        if (!"wiki_tool_event".equals(event)) return null;
        String name = data.path("name").asText("");
        if (!"create_wiki_folder".equals(name) && !"create_wiki_document".equals(name)
                && !"write_wiki_document".equals(name)) return null;
        wikiWriteAttempted = true;
        JsonNode result = data.path("result");
        if (result.path("saved").isBoolean() && !result.path("saved").asBoolean()) return null;
        String spaceId = result.path("spaceId").asText("");
        if (!spaceId.matches("[1-9][0-9]{0,18}")) return null;
        String label = choices.displayLabelFor(owner, spaceId);
        if ("create_wiki_folder".equals(name)) {
            String id = result.path("folderId").asText("");
            String displayName = result.path("name").asText("").trim();
            if (id.matches("[1-9][0-9]{0,18}") && !displayName.isEmpty()) {
                this.spaceId = spaceId;
                spaceLabel = label;
                folderId = id;
                folderName = displayName;
            }
        } else {
            String id = result.path("documentId").asText("");
            String resultFolderId = result.path("folderId").asText("");
            String resultFolderName = result.path("folderName").asText("").trim();
            String title = result.path("title").asText("").trim();
            boolean validFolder = folderId == null
                    ? resultFolderId.matches("[1-9][0-9]{0,18}") && !resultFolderName.isEmpty()
                    : folderId.equals(resultFolderId);
            if (id.matches("[1-9][0-9]{0,18}") && !title.isEmpty() && validFolder) {
                this.spaceId = spaceId;
                spaceLabel = label;
                if (folderId == null) {
                    folderId = resultFolderId;
                    folderName = resultFolderName;
                }
                documentTitle = title;
                byte[] random = new byte[32];
                RANDOM.nextBytes(random);
                ObjectNode state = JSON.createObjectNode();
                state.put("kind", "wiki_write_result");
                state.put("ownerId", owner.getId().toString());
                state.put("conversationId", Long.toString(conversationId));
                state.put("turnId", Long.toString(turnId));
                state.put("spaceId", spaceId);
                state.put("documentId", id);
                state.put("spaceLabel", spaceLabel);
                state.put("folderName", folderName);
                state.put("documentTitle", documentTitle);
                state.put("openToken", Base64.getUrlEncoder().withoutPadding().encodeToString(random));
                interactionJson = state.toString();
                interactionKind = "wiki_write_result";
            }
        }
        return null;
    }

    @Override public String interactionJson() {
        if (interactionJson == null && folderId != null && documentTitle == null) {
            ObjectNode state = JSON.createObjectNode();
            state.put("kind", "wiki_partial_result");
            state.put("ownerId", owner.getId().toString());
            state.put("conversationId", Long.toString(conversationId));
            state.put("turnId", Long.toString(turnId));
            state.put("spaceId", spaceId);
            state.put("folderId", folderId);
            state.put("spaceLabel", spaceLabel);
            state.put("folderName", folderName);
            interactionJson = state.toString();
            interactionKind = "wiki_partial_result";
        }
        return interactionJson;
    }

    @Override public String displayAnswer() {
        if ("destination_choice".equals(interactionKind)) return "请选择写入空间";
        if (documentTitle != null) return "已在" + spaceLabel +
                (folderName == null ? "" : "的文件夹「" + folderName + "」") + "创建文档「" + documentTitle + "」。";
        if (folderName != null) return "已在" + spaceLabel + "创建文件夹「" + folderName + "」，文档尚未保存。";
        return wikiWriteAttempted ? "文档未保存，请检查请求后重试。" : null;
    }
}

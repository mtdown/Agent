package com.et.cloud.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.constant.UserConstant;
import org.springframework.mock.web.MockHttpServletRequest;
import com.et.cloud.service.impl.AgentWikiCreationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@Transactional
class AgentWikiChoiceLocalIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AgentConversationStore conversations;
    @Autowired private AgentWikiChoiceService choices;
    @Autowired private AgentPreviewController controller;
    @Autowired private AgentWikiCreationService creation;

    private User owner;
    private long spaceId;

    @BeforeEach void fixture() {
        jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)",
                "choice-it-" + UUID.randomUUID(), "test-only", UserConstant.DEFAULT_ROLE);
        long userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        owner = new User(); owner.setId(userId);
        jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)",
                "个人区-" + userId, userId);
        spaceId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test void existingConversationTableGainsInternalChoiceFields() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:mysql://localhost:3307/Cloud?"));
            try (ResultSet fields = connection.getMetaData().getColumns(null, null, "agent_message", "interactionJson")) {
                assertTrue(fields.next(), "existing local database must gain interactionJson");
            }
            try (ResultSet fields = connection.getMetaData().getColumns(null, null, "agent_message", "choiceConsumed")) {
                assertTrue(fields.next(), "existing local database must gain choiceConsumed");
            }
        }
    }

    @Test void choiceUsesOpaqueTokenAndCanOnlyBeConsumedOnceByItsOwner() throws Exception {
        long conversationId = conversations.create(owner.getId());
        long turnId = conversations.begin(owner.getId(), conversationId, "保存正文", "OFF", "{}");
        String raw = "{\"choiceRequired\":true,\"recommendedSpaceId\":\"" + spaceId + "\","
                + "\"candidates\":[{\"id\":\"" + spaceId + "\",\"type\":\"personal\","
                + "\"name\":\"个人区-" + owner.getId() + "\"}]}";
        AgentWikiChoiceService.Prepared prepared = choices.prepare(owner, conversationId, turnId,
                JSON.readTree(raw));
        String display = JSON.writeValueAsString(prepared.display);
        assertTrue(display.contains("个人空间"));
        assertFalse(display.contains(Long.toString(owner.getId())));
        assertFalse(display.contains(Long.toString(spaceId)));
        assertTrue(prepared.internalJson.contains(Long.toString(spaceId)));
        conversations.complete(owner.getId(), conversationId, turnId, "请选择写入空间", "[]", "{}",
                prepared.internalJson);
        java.util.List<java.util.Map<String, Object>> history = conversations.messages(owner.getId(), conversationId);
        java.util.Map<String, Object> assistant = history.stream()
                .filter(item -> "assistant".equals(item.get("role"))).findFirst().orElseThrow();
        assertTrue(assistant.containsKey("interaction"));
        assertFalse(assistant.containsKey("interactionJson"));
        String displayHistory = String.valueOf(assistant.get("interaction"));
        assertTrue(displayHistory.contains("个人空间"));
        assertFalse(displayHistory.contains("个人区-" + owner.getId()));
        assertFalse(displayHistory.contains("spaceId"));
        String token = String.valueOf(((java.util.List<?>) prepared.display.get("options")).stream()
                .map(item -> ((java.util.Map<?, ?>) item).get("token")).findFirst().orElseThrow());
        jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)",
                "choice-preview-stranger-" + UUID.randomUUID(), "test-only", UserConstant.DEFAULT_ROLE);
        User stranger = new User(); stranger.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
        assertThrows(IllegalArgumentException.class,
                () -> choices.claim(stranger, conversationId, turnId, token));
        AgentWikiChoiceService.Selection selected = choices.claim(owner, conversationId, turnId, token);
        assertEquals(Long.toString(spaceId), selected.spaceId);
        assertEquals("保存正文", selected.originalQuery);
        assertThrows(IllegalArgumentException.class,
                () -> choices.claim(owner, conversationId, turnId, token));
    }

    @Test void newerTurnInvalidatesOlderChoice() throws Exception {
        long conversationId = conversations.create(owner.getId());
        long turnId = conversations.begin(owner.getId(), conversationId, "保存正文", "OFF", "{}");
        AgentWikiChoiceService.Prepared prepared = choices.prepare(owner, conversationId, turnId,
                JSON.readTree("{\"choiceRequired\":true,\"candidates\":[{\"id\":\"" + spaceId
                        + "\",\"type\":\"personal\"}]}"));
        conversations.complete(owner.getId(), conversationId, turnId, "请选择写入空间", "[]", "{}",
                prepared.internalJson);
        String token = String.valueOf(((java.util.Map<?, ?>) ((java.util.List<?>) prepared.display.get("options"))
                .get(0)).get("token"));
        long newer = conversations.begin(owner.getId(), conversationId, "另一个问题", "OFF", "{}");
        conversations.complete(owner.getId(), conversationId, newer, "回答", "[]", "{}");
        assertThrows(IllegalArgumentException.class,
                () -> choices.claim(owner, conversationId, turnId, token));
    }

    @Test void internalChoiceEventProjectsToDisplayWithoutIds() throws Exception {
        long conversationId = conversations.create(owner.getId());
        long turnId = conversations.begin(owner.getId(), conversationId, "写入文档", "OFF", "{}");
        AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, owner, conversationId, turnId);
        AgentPreviewProxy.PublicEvent shown = projection.project("wiki_choice_request", JSON.readTree(
                "{\"choiceRequired\":true,\"candidates\":[{\"id\":\"" + spaceId + "\"}]}"));
        assertEquals("wiki_choice", shown.name);
        String display = JSON.writeValueAsString(shown.data);
        assertTrue(display.contains("个人空间"));
        assertFalse(display.contains(Long.toString(spaceId)));
        assertTrue(projection.interactionJson().contains(Long.toString(spaceId)));
        assertNull(projection.project("wiki_tool_event", JSON.readTree("{\"folderId\":\"" + spaceId + "\"}")));
    }

    @Test void wikiToolEventsBecomeSafeSuccessOrPartialFailureText() throws Exception {
        long conversationId = conversations.create(owner.getId());
        long turnId = conversations.begin(owner.getId(), conversationId, "保存内容", "OFF", "{}");
        AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, owner, conversationId, turnId);
        AgentWikiCreationService.FolderResult realFolder = creation.createFolder(owner.getId(), Long.toString(spaceId), "任务");
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_folder\",\"result\":" + JSON.writeValueAsString(realFolder) + "}"));
        assertFalse(projection.displayAnswer().contains("文档尚未保存"));
        assertFalse(projection.displayAnswer().contains(Long.toString(spaceId)));
        AgentWikiCreationService.DocumentResult realDoc = creation.createDocument(owner.getId(), Long.toString(spaceId), realFolder.folderId, "测试文档", "正文");
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_document\",\"result\":" + JSON.writeValueAsString(realDoc) + "}"));
        assertTrue(projection.displayAnswer().contains("测试文档"));
        assertTrue(projection.displayAnswer().contains("个人空间"));
        assertFalse(projection.displayAnswer().contains(realDoc.documentId));
        assertFalse(projection.displayAnswer().contains("个人区-"));
    }

    @Test void folderOnlyResultSurvivesConversationReloadWithoutInternalIds() throws Exception {
        long conversationId = conversations.create(owner.getId());
        long turnId = conversations.begin(owner.getId(), conversationId, "保存正文", "OFF", "{}");
        AgentWikiCreationService.FolderResult folder = creation.createFolder(owner.getId(),
                Long.toString(spaceId), "整理失败任务");
        AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, owner, conversationId, turnId);
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_folder\",\"result\":"
                + JSON.writeValueAsString(folder) + "}"));
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_document\","
                + "\"result\":{\"saved\":false,\"error\":\"document save failed\"}}"));
        String internal = projection.interactionJson();
        assertNotNull(internal, "partial success must have a structured interaction");
        java.util.Map<String, Object> safe = AgentWikiChoiceService.safeDisplay(internal, false);
        assertEquals("wiki_partial_result", safe.get("kind"));
        assertEquals("个人空间", safe.get("spaceLabel"));
        assertEquals(folder.name, safe.get("folderName"));
        assertFalse(safe.containsKey("openToken"));
        assertFalse(JSON.writeValueAsString(safe).contains(folder.folderId));
        conversations.complete(owner.getId(), conversationId, turnId, projection.displayAnswer(), "[]", "{}", internal);
        java.util.Map<String, Object> assistant = conversations.messages(owner.getId(), conversationId).stream()
                .filter(item -> "assistant".equals(item.get("role"))).findFirst().orElseThrow();
        assertEquals(safe, assistant.get("interaction"));
    }

    @Test void legacyWriteResultContainsActualFolderNameAfterCollision() throws Exception {
        long conversationId = conversations.create(owner.getId());
        long turnId = conversations.begin(owner.getId(), conversationId, "保存正文", "OFF", "{}");
        AgentWikiCreationService.FolderResult first = creation.createFolder(owner.getId(),
                Long.toString(spaceId), "兼容任务");
        AgentWikiCreationService.DocumentResult document = creation.createLegacyDocument(owner.getId(),
                Long.toString(spaceId), "兼容任务", "# 正文");
        String actualFolderName = jdbc.queryForObject("SELECT name FROM wiki_folder WHERE id=?", String.class,
                Long.parseLong(document.folderId));
        assertEquals(first.name + "（1）", actualFolderName);
        JsonNode toolResult = JSON.valueToTree(document);
        assertEquals(actualFolderName, toolResult.path("folderName").asText());
        AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, owner, conversationId, turnId);
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"write_wiki_document\",\"result\":"
                + JSON.writeValueAsString(document) + "}"));
        java.util.Map<String, Object> safe = AgentWikiChoiceService.safeDisplay(projection.interactionJson(), false);
        assertEquals(actualFolderName, safe.get("folderName"));
        conversations.complete(owner.getId(), conversationId, turnId, projection.displayAnswer(), "[]", "{}",
                projection.interactionJson());
        java.util.Map<String, Object> assistant = conversations.messages(owner.getId(), conversationId).stream()
                .filter(item -> "assistant".equals(item.get("role"))).findFirst().orElseThrow();
        assertEquals(actualFolderName, ((java.util.Map<?, ?>) assistant.get("interaction")).get("folderName"));
    }

    @Test void selectedOptionStartsAContinuationTurnAndCannotBeReplayed() throws Exception {
        long conversationId = conversations.create(owner.getId());
        long turnId = conversations.begin(owner.getId(), conversationId, "保存正文", "OFF", "{}");
        AgentWikiChoiceService.Prepared prepared = choices.prepare(owner, conversationId, turnId,
                JSON.readTree("{\"choiceRequired\":true,\"candidates\":[{\"id\":\"" + spaceId + "\"}]}"));
        conversations.complete(owner.getId(), conversationId, turnId, "请选择写入空间", "[]", "{}",
                prepared.internalJson);
        String token = String.valueOf(((java.util.Map<?, ?>) ((java.util.List<?>) prepared.display.get("options"))
                .get(0)).get("token"));
        AgentPreviewController.WikiChoice body = new AgentPreviewController.WikiChoice();
        body.turnId = Long.toString(turnId);
        body.token = token;
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, owner);
        assertNotNull(controller.selectWikiChoice(Long.toString(conversationId), body, request));
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM agent_message WHERE conversationId=? "
                + "AND role='user'", Long.class, conversationId));
        assertThrows(IllegalArgumentException.class,
                () -> controller.selectWikiChoice(Long.toString(conversationId), body, request));
    }

    @Test void successfulWriteHasOpaquePreviewActionAndRechecksOwnership() throws Exception {
        long conversationId = conversations.create(owner.getId());
        long turnId = conversations.begin(owner.getId(), conversationId, "保存正文", "OFF", "{}");
        AgentWikiCreationService.FolderResult folder = creation.createFolder(owner.getId(),
                Long.toString(spaceId), "整理任务");
        AgentWikiCreationService.DocumentResult document = creation.createDocument(owner.getId(),
                Long.toString(spaceId), folder.folderId, "测试文档", "# 正文");
        AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, owner, conversationId, turnId);
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_folder\",\"result\":"
                + JSON.writeValueAsString(folder) + "}"));
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_document\",\"result\":"
                + JSON.writeValueAsString(document) + "}"));
        conversations.complete(owner.getId(), conversationId, turnId, projection.displayAnswer(), "[]", "{}",
                projection.interactionJson());
        java.util.Map<String, Object> assistant = conversations.messages(owner.getId(), conversationId).stream()
                .filter(item -> "assistant".equals(item.get("role"))).findFirst().orElseThrow();
        String display = String.valueOf(assistant.get("interaction"));
        assertTrue(display.contains("测试文档"));
        assertFalse(display.contains(document.documentId));
        assertFalse(display.contains(folder.folderId));
        java.util.Map<?, ?> interaction = (java.util.Map<?, ?>) assistant.get("interaction");
        String openToken = String.valueOf(interaction.get("openToken"));
        assertEquals("# 正文", choices.preview(owner, conversationId, turnId, openToken).get("content"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, owner);
        assertEquals("# 正文", controller.previewWikiResult(Long.toString(conversationId),
                Long.toString(turnId), openToken, request).getData().get("content"));
        jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)",
                "choice-preview-stranger-" + UUID.randomUUID(), "test-only", UserConstant.DEFAULT_ROLE);
        User stranger = new User(); stranger.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
        assertThrows(IllegalArgumentException.class,
                () -> choices.preview(stranger, conversationId, turnId, openToken));
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, stranger);
        assertThrows(IllegalArgumentException.class,
                () -> controller.previewWikiResult(Long.toString(conversationId),
                        Long.toString(turnId), openToken, request));
    }

    @Test void unknownWikiResultPersistsAndReloadsWithoutMisleadingPartialCardOrInternalIds() throws Exception {
        long conversationId = conversations.create(owner.getId());
        long turnId = conversations.begin(owner.getId(), conversationId, "保存正文", "OFF", "{}");
        AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, owner, conversationId, turnId);
        AgentWikiCreationService.FolderResult realFolder = creation.createFolder(owner.getId(), Long.toString(spaceId), "任务");
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_folder\",\"result\":" + JSON.writeValueAsString(realFolder) + "}"));
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_document\",\"result\":{\"code\":50001,\"status\":\"unknown\"}}"));
        assertTrue(projection.displayAnswer().contains("结果未知"));
        assertNotNull(projection.interactionJson());
        conversations.complete(owner.getId(), conversationId, turnId, projection.displayAnswer(), "[]", "{}", projection.interactionJson());
        java.util.Map<String, Object> assistant = conversations.messages(owner.getId(), conversationId).stream()
                .filter(item -> "assistant".equals(item.get("role"))).findFirst().orElseThrow();
        String history = String.valueOf(assistant);
        assertTrue(history.contains("结果未知"));
        assertTrue(history.contains("任务"));
        assertFalse(history.contains("文档未保存"));
        assertFalse(history.contains("wiki_partial_result"));
        assertFalse(history.contains("987654321098765432"));
        assertFalse(history.contains("spaceId"));
    }

    @Test void confirmedDocumentAndLaterUnknownPersistAsDistinctOutcomes() throws Exception {
        long conversationId = conversations.create(owner.getId());
        long turnId = conversations.begin(owner.getId(), conversationId, "保存正文", "OFF", "{}");
        AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, owner, conversationId, turnId);
        AgentWikiCreationService.FolderResult realFolder = creation.createFolder(owner.getId(), Long.toString(spaceId), "任务");
        AgentWikiCreationService.DocumentResult realDoc = creation.createDocument(owner.getId(), Long.toString(spaceId), realFolder.folderId, "First", "正文");
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_document\",\"result\":" + JSON.writeValueAsString(realDoc) + "}"));
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"write_wiki_document\",\"result\":{\"status\":\"unknown\"}}"));
        conversations.complete(owner.getId(), conversationId, turnId, projection.displayAnswer(), "[]", "{}", projection.interactionJson());
        java.util.Map<String, Object> assistant = conversations.messages(owner.getId(), conversationId).stream()
                .filter(item -> "assistant".equals(item.get("role"))).findFirst().orElseThrow();
        String history = String.valueOf(assistant);
        assertTrue(history.contains("First")); assertTrue(history.contains("另一次写入结果未知"));
        assertTrue(history.contains("wiki_write_result")); assertFalse(history.contains("未保存"));
        assertFalse(history.contains("987654321098765433")); assertFalse(history.contains("spaceId"));
    }
}

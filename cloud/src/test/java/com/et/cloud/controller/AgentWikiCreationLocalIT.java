package com.et.cloud.controller;

import com.et.cloud.model.constant.UserConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.et.cloud.rag.WikiDocumentChangedEvent;
import com.et.cloud.service.impl.AgentWikiCreationService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "agent.internal-key=local-test-secret")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@RecordApplicationEvents
class AgentWikiCreationLocalIT {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private AgentWikiCreationService creation;
    @Autowired private SqlSessionTemplate sqlSession;
    @Autowired private ApplicationEvents events;
    @Autowired private StringRedisTemplate redis;

    private Long userId;
    private Long spaceId;

    @BeforeEach void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:mysql://localhost:3307/Cloud?"));
        }
        jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)",
                "wiki-create-it-" + UUID.randomUUID(), "test-only", UserConstant.DEFAULT_ROLE);
        userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)",
                "wiki-create-it-" + UUID.randomUUID(), userId);
        spaceId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test void createsTaskFolderWithDateAndNumberedCollision() throws Exception {
        String first = createFolder("整理发布说明");
        String second = createFolder("整理发布说明");
        String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        assertEquals("整理发布说明 " + date, jdbc.queryForObject(
                "SELECT name FROM wiki_folder WHERE id=?", String.class, Long.valueOf(first)));
        assertEquals("整理发布说明 " + date + "（1）", jdbc.queryForObject(
                "SELECT name FROM wiki_folder WHERE id=?", String.class, Long.valueOf(second)));
        assertNotEquals(first, second);
    }

    @Test void rejectsBlankTaskAndKeepsLongUnicodeNameValid() throws Exception {
        assertThrows(com.et.cloud.exception.BusinessException.class,
                () -> creation.createFolder(userId, spaceId.toString(), "  \n  "));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_folder WHERE spaceId=?",
                Long.class, spaceId));
        String folderId = createFolder("😀".repeat(150));
        String name = jdbc.queryForObject("SELECT name FROM wiki_folder WHERE id=?",
                String.class, Long.valueOf(folderId));
        assertTrue(name.length() <= 128);
        assertTrue(java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(name));
        assertTrue(name.substring(0, name.length() - 11).codePoints().allMatch(codePoint -> codePoint == 0x1F600));
        assertTrue(name.endsWith(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()));
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFolderCreatesGetDifferentNames() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            String cacheKey = "agentWiki:documentWiki:list:" + spaceId + ":creation-it";
            redis.opsForValue().set(cacheKey, "stale");
            java.util.concurrent.Callable<String> write = () -> {
                start.await();
                return creation.createFolder(userId, spaceId.toString(), "并发任务").name;
            };
            Future<String> first = pool.submit(write);
            Future<String> second = pool.submit(write);
            start.countDown();
            String nameA = first.get();
            String nameB = second.get();
            String base = "并发任务 " + LocalDate.now(ZoneId.of("Asia/Shanghai"));
            assertNotEquals(nameA, nameB);
            assertTrue(java.util.Set.of(nameA, nameB).containsAll(java.util.Set.of(base, base + "（1）")));
            assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_folder WHERE spaceId=?",
                    Long.class, spaceId));
            assertNull(redis.opsForValue().get(cacheKey));
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM wiki_folder WHERE spaceId=?", spaceId);
            jdbc.update("DELETE FROM wiki_space WHERE id=?", spaceId);
            jdbc.update("DELETE FROM user WHERE id=?", userId);
        }
    }

    @Test void createsMarkdownDocumentInFolderWithDerivedTitleAndCollision() throws Exception {
        String folderId = createFolder("整理发布说明");
        com.fasterxml.jackson.databind.JsonNode first = createDocument(folderId, "", "# 发布说明\n\n正文内容");
        com.fasterxml.jackson.databind.JsonNode second = createDocument(folderId, "", "# 发布说明\n\n后续内容");
        assertEquals(spaceId.toString(), first.path("spaceId").asText());
        assertEquals(folderId, first.path("folderId").asText());
        assertTrue(first.path("documentId").asText().matches("[1-9][0-9]+"));
        assertEquals("发布说明", first.path("title").asText());
        assertEquals("发布说明（1）", second.path("title").asText());
        assertNotEquals(first.path("documentId").asText(), second.path("documentId").asText());
        assertEquals("markdown", jdbc.queryForObject("SELECT contentFormat FROM document_wiki WHERE id=?",
                String.class, Long.valueOf(first.path("documentId").asText())));
        assertEquals("NATIVE", jdbc.queryForObject("SELECT sourceType FROM document_wiki WHERE id=?",
                String.class, Long.valueOf(first.path("documentId").asText())));
        assertEquals("发布说明", jdbc.queryForObject("SELECT title FROM document_wiki WHERE id=?",
                String.class, Long.valueOf(first.path("documentId").asText())));
        assertEquals("# 发布说明\n\n正文内容", jdbc.queryForObject("SELECT content FROM document_wiki WHERE id=?",
                String.class, Long.valueOf(first.path("documentId").asText())));
        assertEquals(1, jdbc.queryForObject("SELECT contentVersion FROM document_wiki WHERE id=?",
                Integer.class, Long.valueOf(first.path("documentId").asText())));
        assertTrue(events.stream(WikiDocumentChangedEvent.class).anyMatch(event ->
                event.getChangeType() == WikiDocumentChangedEvent.ChangeType.DOC_CREATED
                        && Long.toString(event.getDocId()).equals(first.path("documentId").asText())
                        && event.getSpaceId().equals(spaceId)));
    }

    @Test void rejectsCrossSpaceDeletedFolderAndRevokedTeamPermission() {
        String original = creation.createFolder(userId, spaceId.toString(), "原空间").folderId;
        jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(1,?,?,0)",
                "other-" + UUID.randomUUID(), userId);
        Long otherSpaceId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO wiki_space_user(spaceId,userId,spaceRole,isDelete) VALUES(?,?,?,0)",
                otherSpaceId, userId, "editor");
        assertThrows(com.et.cloud.exception.BusinessException.class,
                () -> creation.createDocument(userId, otherSpaceId.toString(), original, "跨空间", "正文"));
        jdbc.update("UPDATE wiki_folder SET isDelete=1 WHERE id=?", Long.valueOf(original));
        sqlSession.clearCache();
        assertThrows(com.et.cloud.exception.BusinessException.class,
                () -> creation.createDocument(userId, spaceId.toString(), original, "已删除", "正文"));

        jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(1,?,?,0)",
                "team-" + UUID.randomUUID(), userId);
        Long teamId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO wiki_space_user(spaceId,userId,spaceRole,isDelete) VALUES(?,?,?,0)",
                teamId, userId, "editor");
        String teamFolder = creation.createFolder(userId, teamId.toString(), "团队任务").folderId;
        jdbc.update("UPDATE wiki_space_user SET spaceRole='viewer' WHERE spaceId=? AND userId=?", teamId, userId);
        sqlSession.clearCache();
        assertThrows(com.et.cloud.exception.BusinessException.class,
                () -> creation.createDocument(userId, teamId.toString(), teamFolder, "撤权", "正文"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE folderId IN (?,?)",
                Long.class, Long.valueOf(original), Long.valueOf(teamFolder)));
    }

    @Test void failedDocumentValidationDoesNotLeaveARecord() {
        String folderId = creation.createFolder(userId, spaceId.toString(), "失败任务").folderId;
        assertThrows(com.et.cloud.exception.BusinessException.class,
                () -> creation.createDocument(userId, spaceId.toString(), folderId, "空内容", "  "));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE folderId=?",
                Long.class, Long.valueOf(folderId)));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_folder WHERE id=?",
                Long.class, Long.valueOf(folderId)));
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void legacyWriteRollsBackItsNewFolderWhenDocumentFails() {
        try {
            assertThrows(com.et.cloud.exception.BusinessException.class,
                    () -> creation.createLegacyDocument(userId, spaceId.toString(), "失败旧写入", "  "));
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_folder WHERE spaceId=?",
                    Long.class, spaceId));
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE spaceId=?",
                    Long.class, spaceId));
        } finally {
            jdbc.update("DELETE FROM wiki_space WHERE id=?", spaceId);
            jdbc.update("DELETE FROM user WHERE id=?", userId);
        }
    }

    @Test void legacyWriteAlsoCreatesTaskFolder() throws Exception {
        String response = mvc.perform(post("/internal/agent/wiki-documents")
                        .header("X-Agent-Key", "local-test-secret")
                        .param("userId", userId.toString())
                        .contentType("application/json")
                        .content("{\"spaceId\":\"" + spaceId + "\",\"title\":\"旧工具标题\",\"content\":\"正文\"}"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.JsonNode result = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).path("data");
        assertTrue(result.path("folderId").asText().matches("[1-9][0-9]+"));
        assertTrue(result.path("documentId").asText().matches("[1-9][0-9]+"));
        assertEquals(result.path("folderId").asText(), jdbc.queryForObject(
                "SELECT folderId FROM document_wiki WHERE id=?", String.class,
                Long.valueOf(result.path("documentId").asText())));
    }

    private String createFolder(String taskName) throws Exception {
        String body = "{\"spaceId\":\"" + spaceId + "\",\"taskName\":\"" + taskName + "\"}";
        String response = mvc.perform(post("/internal/agent/wiki-folders")
                        .header("X-Agent-Key", "local-test-secret")
                        .param("userId", userId.toString())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response)
                .path("data").path("folderId").asText();
    }

    private com.fasterxml.jackson.databind.JsonNode createDocument(String folderId, String title, String content)
            throws Exception {
        String body = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put("spaceId", spaceId.toString()).put("folderId", folderId)
                .put("title", title).put("content", content).toString();
        String response = mvc.perform(post("/internal/agent/wiki-documents/create")
                        .header("X-Agent-Key", "local-test-secret")
                        .param("userId", userId.toString())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("data");
    }
}

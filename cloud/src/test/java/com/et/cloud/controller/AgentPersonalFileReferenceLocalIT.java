package com.et.cloud.controller;

import com.et.cloud.model.constant.UserConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local") @Transactional
class AgentPersonalFileReferenceLocalIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired AgentConversationStore conversations;
    @Autowired com.et.cloud.service.UserService users;
    @Autowired com.et.cloud.rag.RagSearchService search;
    @Autowired com.et.cloud.service.WikiSpaceService spaces;
    @Autowired AgentSkillCatalog catalog;
    @Autowired AgentWikiChoiceService choices;
    @TempDir Path root;
    @Test void realDatabasePersistsDistinctReferencesAndEnforcesOwnerMessageAndVersion() throws Exception {
        long owner = user(), stranger = user();
        AgentPersonalFileStore files = new AgentPersonalFileStore(root);
        files.write(owner, "a.txt", "甲乙甲", "create"); files.write(owner, "b.txt", "丙丁", "create");
        AgentResourceInternalController internal = new AgentResourceInternalController(files, "secret");
        AgentPersonalFileReferences references = new AgentPersonalFileReferences(files, conversations, "secret");
        var collector = references.collect(owner); ObjectMapper json = new ObjectMapper();
        collector.accept(json.valueToTree(internal.searchFile("secret", Long.toString(owner), "a.txt", "甲", null).getData().get("referenceEvidence")));
        collector.accept(json.valueToTree(internal.readSegment("secret", Long.toString(owner), "b.txt", null).getData().get("referenceEvidence")));
        collector.accept(json.readTree("[\"forged\"]"));
        long conversation = conversations.create(owner), turn = conversations.begin(owner, conversation, "question", "OFF", "{}");
        conversations.complete(owner, conversation, turn, "[999]", "[]", collector.stats("{\"totalMs\":1}"));
        Map<String,Object> row = conversations.messages(owner, conversation).stream().filter(r -> "assistant".equals(r.get("role"))).findFirst().orElseThrow();
        long message = Long.parseLong(row.get("id").toString());
        var tokens = (List<?>) row.get("personalFileReferences"); assertEquals(3, tokens.size());
        assertFalse(row.toString().contains("a.txt")); assertFalse(row.toString().contains("甲乙甲"));
        String a = ((Map<?,?>) tokens.get(0)).get("token").toString();
        String a2 = ((Map<?,?>) tokens.get(1)).get("token").toString();
        String b = ((Map<?,?>) tokens.get(2)).get("token").toString();
        var info = references.previewInfo(owner, conversation, message);
        assertEquals("a.txt", info.get(0).get("name"));
        var preview = references.preview(owner, conversation, message, a, null);
        assertEquals("甲乙甲", preview.get("text"));
        assertThrows(IllegalArgumentException.class, () -> references.preview(stranger, conversation, message, a, null));
        assertThrows(IllegalArgumentException.class, () -> references.preview(owner, conversation, message, a, "forged"));
        assertEquals(0L, references.resolve(owner, conversation, message, a).get("start"));
        assertEquals(6L, references.resolve(owner, conversation, message, a2).get("start"));
        assertEquals("b.txt", references.resolve(owner, conversation, message, b).get("path"));
        assertThrows(IllegalArgumentException.class, () -> references.resolve(stranger, conversation, message, a));
        assertThrows(IllegalArgumentException.class, () -> references.resolve(owner, conversation, turn, a));
        assertThrows(IllegalArgumentException.class, () -> references.resolve(owner, conversation, message, UUID.randomUUID().toString()));
        long nextTurn = conversations.begin(owner, conversation, "next", "OFF", "{}");
        conversations.complete(owner, conversation, nextTurn, "answer", "[]", "{}");
        long otherMessage = conversations.messages(owner, conversation).stream().filter(r -> "assistant".equals(r.get("role")) && Long.toString(nextTurn).equals(String.valueOf(r.get("turnId"))))
                .map(r -> Long.parseLong(r.get("id").toString())).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> references.resolve(owner, conversation, otherMessage, a));
        files.write(owner, "a.txt", "modified", "overwrite");
        assertThrows(IllegalArgumentException.class, () -> references.resolve(owner, conversation, message, a));
        assertEquals("b.txt", references.resolve(owner, conversation, message, b).get("path"));
        java.nio.file.Files.delete(root.resolve(Long.toString(owner)).resolve("b.txt"));
        assertThrows(IllegalArgumentException.class, () -> references.resolve(owner, conversation, message, b));
    }
    private long user() {
        jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)", "file-ref-it-" + UUID.randomUUID(), "test-only", UserConstant.DEFAULT_ROLE);
        return Objects.requireNonNull(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
    }

    @Test void previewPagesReauthorizeAndRejectTamperingAndStaleVersions() throws Exception {
        long owner = user(), stranger = user();
        var files = new AgentPersonalFileStore(root);
        String text = "甲".repeat(4000) + "标记" + "乙".repeat(8000);
        files.write(owner, "large.txt", text, "create");
        var internal = new AgentResourceInternalController(files, "secret");
        var refs = new AgentPersonalFileReferences(files, conversations, "secret");
        var collector = refs.collect(owner);
        String searchCursor = null;
        do {
            var page = internal.searchFile("secret", Long.toString(owner), "large.txt", "标记", searchCursor).getData();
            collector.accept(new ObjectMapper().valueToTree(page.get("referenceEvidence")));
            searchCursor = (String) page.get("next");
        } while (searchCursor != null);
        long conversation = conversations.create(owner), turn = conversations.begin(owner, conversation, "q", "OFF", "{}");
        conversations.complete(owner, conversation, turn, "answer", "[]", collector.stats("{}"));
        Map<String,Object> row = conversations.messages(owner, conversation).stream().filter(r -> "assistant".equals(r.get("role"))).findFirst().orElseThrow();
        long message = Long.parseLong(row.get("id").toString());
        String token = ((Map<?,?>)((List<?>)row.get("personalFileReferences")).get(0)).get("token").toString();
        Map<String,Object> initial = refs.preview(owner, conversation, message, token, null);
        assertTrue(((String)initial.get("text")).startsWith("标记"));
        String previous = (String) initial.get("previous"), next = (String) initial.get("next");
        assertNotNull(previous); assertNotNull(next);
        Map<String,Object> before = refs.preview(owner, conversation, message, token, previous);
        assertEquals(initial.get("start"), ((Number)before.get("start")).longValue() + ((String)before.get("text")).getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        Map<String,Object> after = refs.preview(owner, conversation, message, token, next);
        assertEquals(((Number)initial.get("start")).longValue() + ((String)initial.get("text")).getBytes(java.nio.charset.StandardCharsets.UTF_8).length, after.get("start"));
        assertThrows(IllegalArgumentException.class, () -> refs.preview(owner, conversation, message, token, next.substring(0, next.length()-1) + "!"));
        assertThrows(IllegalArgumentException.class, () -> refs.preview(stranger, conversation, message, token, next));
        files.write(owner, "large.txt", "new", "overwrite");
        assertThrows(IllegalArgumentException.class, () -> refs.preview(owner, conversation, message, token, next));
    }

    @Test void internalEventPersistsWithoutStreamingEvidenceOrFileContents() throws Exception {
        long owner = user();
        AgentPersonalFileStore files = new AgentPersonalFileStore(root);
        files.write(owner, "private.txt", "private body", "create");
        var data = new AgentResourceInternalController(files, "secret").readSegment("secret", Long.toString(owner), "private.txt", null).getData();
        String evidence = new ObjectMapper().writeValueAsString(data.get("referenceEvidence"));
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/stream", exchange -> {
            byte[] bytes = ("event: personal_file_reference\ndata: " + evidence + "\n\nevent: delta\ndata: {\"content\":\"answer\"}\n\nevent: done\ndata: {}\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            var service = new AgentPersonalFileReferences(files, conversations, "secret");
            var controller = new AgentPreviewController(users, new AgentPreviewProxy("http://127.0.0.1:" + server.getAddress().getPort(), "secret"), conversations, search, spaces, catalog, choices, service);
            var user = new com.et.cloud.model.entity.User(); user.setId(owner);
            var request = new org.springframework.mock.web.MockHttpServletRequest(); request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
            long conversation = conversations.create(owner); var ask = new AgentPreviewController.Ask("question"); ask.conversationId = Long.toString(conversation);
            var output = new java.io.ByteArrayOutputStream(); controller.stream(ask, request).writeTo(output);
            String stream = output.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(stream.contains(evidence)); assertFalse(stream.contains("private body")); assertFalse(stream.contains("private.txt"));
            var row = conversations.messages(owner, conversation).stream().filter(r -> "assistant".equals(r.get("role"))).findFirst().orElseThrow();
            var list = (List<?>) row.get("personalFileReferences"); assertEquals(1, list.size());
            String token = ((Map<?,?>) list.get(0)).get("token").toString();
            assertEquals("private.txt", service.resolve(owner, conversation, Long.parseLong(row.get("id").toString()), token).get("path"));
        } finally { server.stop(0); }
    }
}

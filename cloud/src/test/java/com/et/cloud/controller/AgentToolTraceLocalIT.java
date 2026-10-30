package com.et.cloud.controller;

import com.et.cloud.model.constant.UserConstant;
import com.et.cloud.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local") @Transactional
class AgentToolTraceLocalIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired AgentConversationStore conversations;
    @Autowired com.et.cloud.service.UserService users;
    @Autowired com.et.cloud.rag.RagSearchService search;
    @Autowired com.et.cloud.service.WikiSpaceService spaces;
    @Autowired AgentSkillCatalog catalog;
    @Autowired AgentWikiChoiceService choices;

    @Test void safeTracePersistsWithHistoryAndRejectsOtherOwners() throws Exception {
        jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)", "trace-"+UUID.randomUUID(), "test-only", UserConstant.DEFAULT_ROLE);
        User owner = new User(); owner.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        String payload = "event: done\ndata: {\"toolTraces\":[{\"name\":\"读取文件\",\"status\":\"success\",\"durationMs\":2,\"content\":\"secret-body\"},{\"name\":\"写入文件\",\"status\":\"failed\",\"durationMs\":3},{\"name\":\"保存代码\",\"status\":\"unknown\",\"durationMs\":4},{\"name\":\"创建 Skill\",\"status\":\"blocked\",\"durationMs\":0},{\"name\":\"secret-name\",\"status\":\"success\",\"durationMs\":1}]}\n\n";
        server.createContext("/internal/agent/stream", exchange -> { byte[] bytes=payload.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,bytes.length); try(var out=exchange.getResponseBody()){out.write(bytes);} });
        server.start();
        try {
            var proxy = new AgentPreviewProxy("http://127.0.0.1:"+server.getAddress().getPort(), "test-key");
            var controller = new AgentPreviewController(users,proxy,conversations,search,spaces,catalog,choices);
            var request = new org.springframework.mock.web.MockHttpServletRequest(); request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE,owner);
            long conversation = conversations.create(owner.getId());
            var ask = new AgentPreviewController.Ask("test"); ask.conversationId = Long.toString(conversation);
            var out = new java.io.ByteArrayOutputStream(); controller.stream(ask,request).writeTo(out);
            String live = out.toString(StandardCharsets.UTF_8);
            assertTrue(live.contains("读取文件")); assertFalse(live.contains("secret-body")); assertFalse(live.contains("secret-name"));
            String history = String.valueOf(conversations.messages(owner.getId(),conversation));
            assertTrue(history.contains("读取文件")); assertFalse(history.contains("secret-body")); assertFalse(history.contains("secret-name"));
            for (String status : new String[]{"success", "failed", "unknown", "blocked"}) {
                assertTrue(live.contains(status)); assertTrue(history.contains(status));
            }
            assertTrue(history.indexOf("读取文件") < history.indexOf("写入文件"));
            assertThrows(IllegalArgumentException.class, () -> conversations.messages(owner.getId()+1,conversation));
        } finally { server.stop(0); }
    }

    @Test void interruptedTurnDoesNotProduceCompletedTraceOrHistory() throws Exception {
        jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)", "trace-fail-"+UUID.randomUUID(), "test-only", UserConstant.DEFAULT_ROLE);
        User owner = new User(); owner.setId(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/stream", exchange -> { byte[] bytes="event: error\ndata: {\"message\":\"Agent request failed\"}\n\n".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,bytes.length); try(var out=exchange.getResponseBody()){out.write(bytes);} });
        server.start();
        try {
            var proxy = new AgentPreviewProxy("http://127.0.0.1:"+server.getAddress().getPort(), "test-key");
            var controller = new AgentPreviewController(users,proxy,conversations,search,spaces,catalog,choices);
            var request = new org.springframework.mock.web.MockHttpServletRequest(); request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE,owner);
            long conversation = conversations.create(owner.getId()); var ask = new AgentPreviewController.Ask("test"); ask.conversationId = Long.toString(conversation);
            var out = new java.io.ByteArrayOutputStream(); controller.stream(ask,request).writeTo(out);
            assertFalse(out.toString(StandardCharsets.UTF_8).contains("event: done"));
            assertFalse(String.valueOf(conversations.messages(owner.getId(),conversation)).contains("toolTraces"));
        } finally { server.stop(0); }
    }
}

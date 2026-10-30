package com.et.cloud.controller;

import com.et.cloud.model.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentWikiUnknownProjectionTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static void stubCurrentDisplay(AgentWikiChoiceService choices) {
        when(choices.currentDisplay(any(), anyString(), anyBoolean())).thenAnswer(call -> {
            com.fasterxml.jackson.databind.JsonNode state = JSON.readTree(call.getArgument(1, String.class));
            java.util.Map<String,Object> result = new java.util.LinkedHashMap<>();
            String kind = state.path("kind").asText();
            if ("wiki_write_result".equals(kind)) {
                result.put("kind", kind); result.put("turnId",state.path("turnId").asText());
                result.put("spaceLabel","个人空间"); result.put("folderName",state.path("folderName").asText());
                result.put("documentTitle",state.path("documentTitle").asText()); result.put("openToken",state.path("openToken").asText());
            } else if ("wiki_partial_result".equals(kind)) {
                result.put("kind",kind); result.put("turnId",state.path("turnId").asText());
                result.put("spaceLabel","个人空间"); result.put("folderName",state.path("folderName").asText());
            } else {
                result.put("kind","wiki_write_results"); result.put("turnId",state.path("turnId").asText());
                java.util.List<java.util.Map<String,Object>> items = new java.util.ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode f : state.path("folders")) {
                    java.util.Map<String,Object> item = new java.util.LinkedHashMap<>();
                    item.put("kind","wiki_folder_result"); item.put("spaceLabel","个人空间"); item.put("folderName",f.path("folderName").asText()); items.add(item);
                }
                result.put("results",items); result.put("unknown",state.path("unknown").asInt()); result.put("failed",state.path("failed").asInt());
            }
            return result;
        });
    }

    @Test void unknownWriteDoesNotClaimFailureOrOfferRetry() throws Exception {
        User user = new User(); user.setId(7L);
        AgentWikiChoiceService choices = mock(AgentWikiChoiceService.class);
        stubCurrentDisplay(choices);
        AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, user, 11L, 12L);
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"write_wiki_document\",\"result\":{\"code\":50001,\"status\":\"unknown\"}}"));
        assertTrue(projection.displayAnswer().contains("结果未知"));
        assertFalse(projection.displayAnswer().contains("未保存"));
        assertFalse(projection.displayAnswer().contains("重试"));
        assertNotNull(projection.interactionJson());
        assertEquals("unknown", JSON.readTree(projection.interactionJson()).path("operations").get(0).path("status").asText());
        assertFalse(projection.interactionJson().contains("documentId"));
    }

    @Test void unknownAfterFolderKeepsConfirmedFolderWithoutFalsePartialFailureCard() throws Exception {
        User user = new User(); user.setId(7L);
        AgentWikiChoiceService choices = mock(AgentWikiChoiceService.class);
        stubCurrentDisplay(choices);
        when(choices.displayLabelFor(user, "1234567890123456789")).thenReturn("个人空间");
        AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, user, 11L, 12L);
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_folder\",\"result\":{\"spaceId\":\"1234567890123456789\",\"folderId\":\"9876543210987654321\",\"name\":\"任务\"}}"));
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_document\",\"result\":{\"code\":50001,\"status\":\"unknown\"}}"));
        assertTrue(projection.displayAnswer().contains("任务"));
        assertTrue(projection.displayAnswer().contains("结果未知"));
        assertFalse(projection.displayAnswer().contains("未保存"));
        assertFalse(projection.displayAnswer().contains("9876543210987654321"));
        assertNotNull(projection.interactionJson());
        assertTrue(projection.interactionJson().contains("wiki_write_results"));
    }

    @Test void confirmedFailureAndSuccessRetainTheirExistingProjection() throws Exception {
        User user = new User(); user.setId(7L);
        AgentWikiChoiceService choices = mock(AgentWikiChoiceService.class);
        stubCurrentDisplay(choices);
        when(choices.displayLabelFor(user, "55")).thenReturn("个人空间");
        AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, user, 11L, 12L);
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_folder\",\"result\":{\"spaceId\":\"55\",\"folderId\":\"123\",\"name\":\"任务\"}}"));
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_document\",\"result\":{\"saved\":false}}"));
        assertTrue(projection.displayAnswer().contains("文档尚未保存"));
        assertTrue(projection.interactionJson().contains("wiki_partial_result"));
        AgentWikiChoiceProjection success = new AgentWikiChoiceProjection(choices, user, 11L, 12L);
        success.project("wiki_tool_event", JSON.readTree("{\"name\":\"write_wiki_document\",\"result\":{\"spaceId\":\"55\",\"folderId\":\"123\",\"folderName\":\"任务\",\"documentId\":\"456\",\"title\":\"文档\"}}"));
        assertTrue(success.displayAnswer().contains("文档」"));
        assertFalse(success.displayAnswer().contains("456"));
        assertTrue(success.interactionJson().contains("wiki_write_result"));
    }

    @Test void unknownIsStreamedAndPassedToConversationCompletionWithoutFalseCard() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/stream", exchange -> {
            byte[] body = ("event: wiki_tool_event\ndata: {\"name\":\"create_wiki_folder\",\"result\":{\"spaceId\":\"55\",\"folderId\":\"123\",\"name\":\"任务\"}}\n\n"
                    + "event: wiki_tool_event\ndata: {\"name\":\"create_wiki_document\",\"result\":{\"status\":\"unknown\"}}\n\n"
                    + "event: done\ndata: {}\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            User user = new User(); user.setId(7L);
            AgentWikiChoiceService choices = mock(AgentWikiChoiceService.class);
        stubCurrentDisplay(choices);
            when(choices.displayLabelFor(user, "55")).thenReturn("个人空间");
            AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, user, 11L, 12L);
            AgentPreviewProxy proxy = new AgentPreviewProxy("http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
            java.util.concurrent.atomic.AtomicReference<String[]> completed = new java.util.concurrent.atomic.AtomicReference<>();
            java.io.ByteArrayOutputStream browser = new java.io.ByteArrayOutputStream();
            proxy.stream(java.util.Collections.singletonMap("query", "保存"), null,
                    (ok, output) -> { assertTrue(ok); completed.set(output); }, projection).writeTo(browser);
            String output = browser.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(output.contains("结果未知"));
            assertFalse(output.contains("未保存"));
            assertFalse(output.contains("wiki_partial_result"));
            assertFalse(output.contains("folderId"));
            assertEquals(projection.displayAnswer(), completed.get()[0]);
            assertNotNull(completed.get()[2]);
            assertTrue(completed.get()[2].contains("wiki_write_results"));
        } finally { server.stop(0); }
    }

    @Test void confirmedDocumentAndLaterUnknownRemainDistinct() throws Exception {
        User user = new User(); user.setId(7L);
        AgentWikiChoiceService choices = mock(AgentWikiChoiceService.class);
        stubCurrentDisplay(choices);
        when(choices.displayLabelFor(user, "55")).thenReturn("个人空间");
        AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, user, 11L, 12L);
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"create_wiki_document\",\"result\":{\"spaceId\":\"55\",\"folderId\":\"123\",\"folderName\":\"任务\",\"documentId\":\"456\",\"title\":\"First\"}}"));
        projection.project("wiki_tool_event", JSON.readTree("{\"name\":\"write_wiki_document\",\"result\":{\"status\":\"unknown\"}}"));
        String answer = projection.displayAnswer();
        assertTrue(answer.contains("First"));
        assertTrue(answer.contains("另一次写入"));
        assertTrue(answer.contains("结果未知"));
        assertFalse(answer.contains("456"));
        assertEquals("wiki_write_result", AgentWikiChoiceService.safeDisplay(projection.interactionJson(), false).get("kind"));
    }

    @Test void confirmedThenUnknownStreamShowsSeparatedOutcomesAndSafeCard() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/stream", exchange -> {
            byte[] body = ("event: wiki_tool_event\ndata: {\"name\":\"create_wiki_document\",\"result\":{\"spaceId\":\"55\",\"folderId\":\"123\",\"folderName\":\"任务\",\"documentId\":\"456\",\"title\":\"First\"}}\n\n"
                    + "event: wiki_tool_event\ndata: {\"name\":\"write_wiki_document\",\"result\":{\"status\":\"unknown\"}}\n\n"
                    + "event: done\ndata: {}\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(body); }
        }); server.start();
        try {
            User user = new User(); user.setId(7L);
            AgentWikiChoiceService choices = mock(AgentWikiChoiceService.class);
        stubCurrentDisplay(choices);
            when(choices.displayLabelFor(user, "55")).thenReturn("个人空间");
            AgentWikiChoiceProjection projection = new AgentWikiChoiceProjection(choices, user, 11L, 12L);
            AgentPreviewProxy proxy = new AgentPreviewProxy("http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
            java.util.concurrent.atomic.AtomicReference<String[]> completed = new java.util.concurrent.atomic.AtomicReference<>();
            java.io.ByteArrayOutputStream browser = new java.io.ByteArrayOutputStream();
            proxy.stream(java.util.Collections.singletonMap("query", "保存"), null,
                    (ok, output) -> { assertTrue(ok); completed.set(output); }, projection).writeTo(browser);
            String output = browser.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(output.contains("First")); assertTrue(output.contains("另一次写入结果未知"));
            assertTrue(output.contains("wiki_write_result")); assertFalse(output.contains("documentId"));
            assertFalse(output.contains("456")); assertFalse(output.contains("未保存"));
            assertEquals(projection.displayAnswer(), completed.get()[0]);
        } finally { server.stop(0); }
    }
}

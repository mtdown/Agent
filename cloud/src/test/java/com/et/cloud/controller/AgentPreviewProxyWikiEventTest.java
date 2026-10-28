package com.et.cloud.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AgentPreviewProxyWikiEventTest {
    @Test void internalWikiChoiceIsProjectedBeforeBrowserReceivesIt() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/stream", exchange -> {
            byte[] body = ("event: wiki_choice_request\ndata: {\"choiceRequired\":true,"
                    + "\"candidates\":[{\"id\":\"9007199254740995\"}]}\n\n"
                    + "event: done\ndata: {\"usage\":null}\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream response = exchange.getResponseBody()) { response.write(body); }
        });
        server.start();
        try {
            AgentPreviewProxy proxy = new AgentPreviewProxy("http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
            AtomicReference<String[]> completed = new AtomicReference<>();
            ByteArrayOutputStream browser = new ByteArrayOutputStream();
            proxy.stream(Collections.singletonMap("query", "保存内容"), null,
                    (success, output) -> { assertTrue(success); completed.set(output); },
                    new AgentPreviewProxy.WikiEvents() {
                        @Override public AgentPreviewProxy.PublicEvent project(String event, JsonNode data) {
                            assertEquals("wiki_choice_request", event);
                            assertEquals("9007199254740995", data.path("candidates").get(0).path("id").asText());
                            return new AgentPreviewProxy.PublicEvent("wiki_choice",
                                    Collections.singletonMap("label", "个人空间"));
                        }
                        @Override public String interactionJson() { return "{\"internal\":true}"; }
                    }).writeTo(browser);
            String output = browser.toString(StandardCharsets.UTF_8);
            assertTrue(output.contains("event: wiki_choice"));
            assertTrue(output.contains("个人空间"));
            assertFalse(output.contains("9007199254740995"));
            assertFalse(output.contains("wiki_choice_request"));
            assertEquals("{\"internal\":true}", completed.get()[2]);
        } finally { server.stop(0); }
    }

    @Test void successfulWikiResultExposesOnlyDisplayFields() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/stream", exchange -> {
            byte[] body = ("event: wiki_tool_event\ndata: {\"name\":\"create_wiki_document\","
                    + "\"result\":{\"documentId\":\"9007199254740997\"}}\n\n"
                    + "event: done\ndata: {\"usage\":null}\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream response = exchange.getResponseBody()) { response.write(body); }
        });
        server.start();
        try {
            AgentPreviewProxy proxy = new AgentPreviewProxy("http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
            AtomicReference<String[]> completed = new AtomicReference<>();
            ByteArrayOutputStream browser = new ByteArrayOutputStream();
            proxy.stream(Collections.singletonMap("query", "保存内容"), null,
                    (success, output) -> { assertTrue(success); completed.set(output); },
                    new AgentPreviewProxy.WikiEvents() {
                        @Override public AgentPreviewProxy.PublicEvent project(String event, JsonNode data) {
                            assertEquals("wiki_tool_event", event);
                            return null;
                        }
                        @Override public String interactionJson() {
                            return "{\"kind\":\"wiki_write_result\",\"turnId\":\"11\","
                                    + "\"documentId\":\"9007199254740997\",\"spaceLabel\":\"个人空间\","
                                    + "\"folderName\":\"整理任务\",\"documentTitle\":\"测试文档\","
                                    + "\"openToken\":\"opaque-token\"}";
                        }
                        @Override public String displayAnswer() { return "已创建文档「测试文档」。"; }
                    }).writeTo(browser);
            String output = browser.toString(StandardCharsets.UTF_8);
            assertTrue(output.contains("event: wiki_result"));
            assertTrue(output.contains("测试文档"));
            assertFalse(output.contains("9007199254740997"));
            assertFalse(output.contains("documentId"));
            assertEquals("已创建文档「测试文档」。", completed.get()[0]);
        } finally { server.stop(0); }
    }

    @Test void partialWikiResultStreamsOnlyFolderNameWithoutDocumentAction() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/stream", exchange -> {
            byte[] body = ("event: wiki_tool_event\ndata: {\"name\":\"create_wiki_folder\","
                    + "\"result\":{\"folderId\":\"9007199254740995\"}}\n\n"
                    + "event: done\ndata: {\"usage\":null}\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream response = exchange.getResponseBody()) { response.write(body); }
        });
        server.start();
        try {
            AgentPreviewProxy proxy = new AgentPreviewProxy("http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
            AtomicReference<String[]> completed = new AtomicReference<>();
            ByteArrayOutputStream browser = new ByteArrayOutputStream();
            proxy.stream(Collections.singletonMap("query", "保存内容"), null,
                    (success, output) -> { assertTrue(success); completed.set(output); },
                    new AgentPreviewProxy.WikiEvents() {
                        @Override public AgentPreviewProxy.PublicEvent project(String event, JsonNode data) {
                            assertEquals("wiki_tool_event", event);
                            return null;
                        }
                        @Override public String interactionJson() {
                            return "{\"kind\":\"wiki_partial_result\",\"turnId\":\"11\","
                                    + "\"folderId\":\"9007199254740995\",\"spaceLabel\":\"个人空间\","
                                    + "\"folderName\":\"整理任务\"}";
                        }
                        @Override public String displayAnswer() { return "已创建文件夹「整理任务」，文档尚未保存。"; }
                    }).writeTo(browser);
            String output = browser.toString(StandardCharsets.UTF_8);
            assertTrue(output.contains("event: wiki_result"));
            assertTrue(output.contains("wiki_partial_result"));
            assertTrue(output.contains("整理任务"));
            assertFalse(output.contains("9007199254740995"));
            assertFalse(output.contains("folderId"));
            assertFalse(output.contains("openToken"));
            assertEquals("已创建文件夹「整理任务」，文档尚未保存。", completed.get()[0]);
        } finally { server.stop(0); }
    }
}

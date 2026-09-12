package com.et.cloud.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagEmbeddingClientTest {

    private HttpServer server;

    private RagProperties properties;

    private String lastRequestBody;

    private boolean lastRequestHadAuthHeader;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            lastRequestBody = new String(body, StandardCharsets.UTF_8);
            lastRequestHadAuthHeader = exchange.getRequestHeaders().containsKey("Authorization");
            String response = "{\"data\":[" +
                    "{\"index\":0,\"embedding\":[0.1,0.2,0.3]}," +
                    "{\"index\":1,\"embedding\":[0.4,0.5,0.6]}" +
                    "]}";
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        properties = new RagProperties();
        properties.getEmbedding().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.getEmbedding().setApiKey("test-key");
        properties.getEmbedding().setModel("test-model");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void embedSendsBatchAndParsesVectors() {
        RagEmbeddingClient client = new RagEmbeddingClient(properties);
        List<float[]> vectors = client.embed(List.of("文本一", "文本二"));
        assertEquals(2, vectors.size());
        assertEquals(3, vectors.get(0).length);
        assertEquals(0.1f, vectors.get(0)[0], 1e-6);
        assertEquals(0.6f, vectors.get(1)[2], 1e-6);
        assertTrue(lastRequestBody.contains("test-model"));
        assertTrue(lastRequestBody.contains("文本一"));
        assertTrue(lastRequestBody.contains("文本二"));
    }

    @Test
    void emptyInputReturnsEmpty() {
        RagEmbeddingClient client = new RagEmbeddingClient(properties);
        assertEquals(0, client.embed(List.of()).size());
    }

    @Test
    void missingApiKeyOnCloudEndpointThrowsRecognizableException() {
        properties.getEmbedding().setApiKey("");
        // 云端地址无 key 才算未配置
        properties.getEmbedding().setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        RagEmbeddingClient client = new RagEmbeddingClient(properties);
        RagEmbeddingUnavailableException ex = assertThrows(RagEmbeddingUnavailableException.class,
                () -> client.embed(List.of("文本")));
        assertTrue(ex.getMessage().contains("RAG embedding 未配置"));
    }

    @Test
    void isConfiguredReflectsKeyPresenceAndLocalMode() {
        RagEmbeddingClient client = new RagEmbeddingClient(properties);
        assertTrue(client.isConfigured());
        // 本地端点：无 key 也视为已配置（Ollama 模式）
        properties.getEmbedding().setApiKey(" ");
        assertTrue(client.isConfigured());
        // 云端端点：无 key 才是未配置
        properties.getEmbedding().setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        assertEquals(false, client.isConfigured());
    }

    @Test
    void localModeEmbedsAndOmitsAuthorizationHeader() {
        properties.getEmbedding().setApiKey("");
        RagEmbeddingClient client = new RagEmbeddingClient(properties);
        List<float[]> vectors = client.embed(List.of("本地文本一", "本地文本二"));
        assertEquals(2, vectors.size());
        assertEquals(false, lastRequestHadAuthHeader);
    }

    @Test
    void connectFailureProducesReadableMessageEvenWhenCauseMessageIsNull() {
        // 端口 1 几乎必然无监听：ConnectException 在 Windows 上 message 可能为 null，
        // 报错必须兜底拼异常类名与端点，否则出现"embedding 请求失败: null"这种不可定位信息；
        // 传输层失败会自动重试 2 次，重试耗尽后的报错需带上重试次数
        properties.getEmbedding().setBaseUrl("http://127.0.0.1:1/v1");
        RagEmbeddingClient client = new RagEmbeddingClient(properties);
        long start = System.currentTimeMillis();
        RagEmbeddingUnavailableException ex = assertThrows(RagEmbeddingUnavailableException.class,
                () -> client.embed(List.of("文本")));
        assertTrue(ex.getMessage().contains("embedding 请求失败"),
                "message was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("http://127.0.0.1:1/v1"),
                "message was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("已重试 2 次"),
                "message was: " + ex.getMessage());
        // 300ms + 1000ms 退避确实执行过
        assertTrue(System.currentTimeMillis() - start >= 1300, "backoff delays were skipped");
    }

    @Test
    void retriesTransportFailureAndSucceeds() throws Exception {
        // 第一发踩中已被服务端关闭的池化连接（handler 异常 → 连接被掐，客户端读不到响应头），
        // 第二发新建连接成功 —— 对应云端网关静默回收 keep-alive 连接的场景
        HttpServer flaky = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
        flaky.createContext("/v1/embeddings", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (hits.incrementAndGet() == 1) {
                throw new RuntimeException("simulated stale connection");
            }
            String response = "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}";
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        flaky.start();
        try {
            properties.getEmbedding().setBaseUrl(
                    "http://127.0.0.1:" + flaky.getAddress().getPort() + "/v1");
            RagEmbeddingClient client = new RagEmbeddingClient(properties);
            List<float[]> vectors = client.embed(List.of("文本"));
            assertEquals(1, vectors.size());
            assertEquals(2, vectors.get(0).length);
            assertEquals(2, hits.get(), "first attempt died on the stale connection, retry succeeded");
        } finally {
            flaky.stop(0);
        }
    }
}

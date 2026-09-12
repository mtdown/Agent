package com.et.cloud.rag;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible embedding client (default DashScope compatible-mode).
 * Sends texts in batches to cut request counts during backfill.
 */
@Component
@Slf4j
public class RagEmbeddingClient {

    /**
     * 1 次原始请求 + 2 次重试。embedding 是幂等 POST，可安全重试；
     * 云端网关会静默回收空闲 keep-alive 连接，空闲后第一发常踩死连接
     * 得到 Connection reset，重试即新建连接恢复 —— 不重试则直接对用户报错。
     */
    private static final int MAX_ATTEMPTS = 3;

    private static final long[] BACKOFF_MS = {300L, 1000L};

    private final RagProperties ragProperties;

    private final HttpClient httpClient;

    public RagEmbeddingClient(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Returns true when embedding is usable; callers degrade gracefully otherwise.
     */
    public boolean isConfigured() {
        return ragProperties.getEmbedding().isConfigured();
    }

    /**
     * Embeds a batch of texts. Order of returned vectors matches input order.
     * Retries transport-level failures (stale pooled connection etc.) twice.
     *
     * @throws RagEmbeddingUnavailableException when not configured or the endpoint fails
     */
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        RagProperties.Embedding config = ragProperties.getEmbedding();
        if (!config.isConfigured()) {
            throw new RagEmbeddingUnavailableException(
                    "RAG embedding 未配置：云端需设置 RAG_EMBEDDING_API_KEY（start-dev.ps1 读取根目录 .env.dev），"
                            + "本地 Ollama 则将 base-url 指向 http://localhost:11434/v1");
        }
        HttpRequest request = buildRequest(config, texts);
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    // HTTP 层错误（鉴权/限流/参数）重试无益，直接抛
                    throw new RagEmbeddingUnavailableException(
                            "embedding 响应异常 HTTP " + response.statusCode() + ": " + truncate(response.body()));
                }
                return parseVectors(response.body(), texts.size());
            } catch (IOException e) {
                lastFailure = e;
                log.warn("embedding 第 {}/{} 次尝试失败: {}（端点 {}）",
                        attempt, MAX_ATTEMPTS, describe(e), config.getBaseUrl());
                if (attempt < MAX_ATTEMPTS && !sleepQuietly(BACKOFF_MS[attempt - 1])) {
                    throw new RagEmbeddingUnavailableException("embedding 请求被中断", e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RagEmbeddingUnavailableException("embedding 请求被中断", e);
            }
        }
        throw new RagEmbeddingUnavailableException(
                "embedding 请求失败: " + describe(lastFailure)
                        + "（端点 " + config.getBaseUrl() + "，已重试 " + (MAX_ATTEMPTS - 1) + " 次）", lastFailure);
    }

    private HttpRequest buildRequest(RagProperties.Embedding config, List<String> texts) {
        JSONObject body = new JSONObject();
        body.set("model", config.getModel());
        body.set("input", texts);
        body.set("encoding_format", "float");
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(config.getBaseUrl()) + "/embeddings"))
                .timeout(Duration.ofSeconds(Math.max(5, config.getTimeoutSeconds())))
                .header("Content-Type", "application/json");
        // 本地端点（Ollama）不需要 Authorization 头
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + config.getApiKey());
        }
        return requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
    }

    /** @return false when the sleep was interrupted (caller should abort the retry loop) */
    private static boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Windows 下 ConnectException.getMessage() 常为 null，兜底用异常类名描述 */
    private static String describe(Exception e) {
        if (e == null) {
            return "unknown";
        }
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }

    private List<float[]> parseVectors(String responseBody, int expectedCount) {
        JSONObject json = JSONUtil.parseObj(responseBody);
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.size() != expectedCount) {
            throw new RagEmbeddingUnavailableException(
                    "embedding 返回数量不符: 期望 " + expectedCount + " 实际 " + (data == null ? 0 : data.size()));
        }
        List<float[]> vectors = new ArrayList<>(expectedCount);
        for (int i = 0; i < data.size(); i++) {
            JSONArray embedding = data.getJSONObject(i).getJSONArray("embedding");
            float[] vector = new float[embedding.size()];
            for (int j = 0; j < embedding.size(); j++) {
                vector[j] = embedding.getFloat(j);
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private static String trimTrailingSlash(String url) {
        return url == null ? "" : (url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}

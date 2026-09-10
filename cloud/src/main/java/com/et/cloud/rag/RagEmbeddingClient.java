package com.et.cloud.rag;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
                    "RAG embedding 未配置：云端需设置 RAG_EMBEDDING_API_KEY，本地 Ollama 需将 base-url 指向 http://localhost:11434/v1");
        }
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
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RagEmbeddingUnavailableException("embedding 请求失败: " + e.getMessage(), e);
        }
        if (response.statusCode() != 200) {
            throw new RagEmbeddingUnavailableException(
                    "embedding 响应异常 HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }
        return parseVectors(response.body(), texts.size());
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

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
 * Cross-encoder re-ranking client (default: DashScope {@code gte-rerank-v2}).
 *
 * <p>Role in the pipeline: candidate generation only decides what is *in* the
 * pool; the ranking of the answer slots is what the eval ultimately measures.
 * Measured on the eval set, a perfect re-ranker over the fused candidate pool
 * is worth far more than any further candidate generation,
 * so the pool and the ranking step are deliberately separate concerns.
 *
 * <p>This client is an OPTIONAL channel. {@link #isUsable()} is false when
 * re-ranking is disabled or no key is configured, and every failure surfaces as
 * {@link RagRerankUnavailableException} so the caller can fall back instead of
 * failing the request.
 */
@Component
@Slf4j
public class RagRerankClient {

    /** 1 次原始请求 + 1 次重试（幂等 POST，仅重试传输层失败）。 */
    private static final int MAX_ATTEMPTS = 2;

    private final RagProperties ragProperties;

    private final HttpClient httpClient;

    public RagRerankClient(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** True when re-ranking is enabled and a key is present. */
    public boolean isUsable() {
        return ragProperties.getRetrieval().getRerank().isUsable();
    }

    /**
     * Scores {@code documents} against {@code query} and returns their indices
     * ordered by descending relevance. Only the first {@code topN} indices are
     * returned.
     *
     * @throws RagRerankUnavailableException when not usable, or the endpoint fails
     */
    public List<Integer> rerank(String query, List<String> documents, int topN) {
        RagProperties.Rerank config = ragProperties.getRetrieval().getRerank();
        if (!config.isUsable()) {
            throw new RagRerankUnavailableException("重排未启用或未配置 api-key");
        }
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }
        HttpRequest request = buildRequest(config, query, documents, topN);
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new RagRerankUnavailableException(
                            "重排响应异常 HTTP " + response.statusCode() + ": " + truncate(response.body()));
                }
                return parseIndices(response.body(), documents.size(), topN);
            } catch (IOException e) {
                lastFailure = e;
                log.warn("重排第 {}/{} 次尝试失败: {}（端点 {}）",
                        attempt, MAX_ATTEMPTS, describe(e), config.getBaseUrl());
                if (attempt < MAX_ATTEMPTS && !sleepQuietly(300L)) {
                    throw new RagRerankUnavailableException("重排请求被中断", e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RagRerankUnavailableException("重排请求被中断", e);
            }
        }
        throw new RagRerankUnavailableException(
                "重排请求失败: " + describe(lastFailure) + "（端点 " + config.getBaseUrl() + "）", lastFailure);
    }

    private HttpRequest buildRequest(RagProperties.Rerank config, String query,
                                     List<String> documents, int topN) {
        JSONObject input = new JSONObject();
        input.set("query", query);
        input.set("documents", documents);
        JSONObject parameters = new JSONObject();
        parameters.set("return_documents", false);
        parameters.set("top_n", Math.max(1, Math.min(topN, documents.size())));
        JSONObject body = new JSONObject();
        body.set("model", config.getModel());
        body.set("input", input);
        body.set("parameters", parameters);
        return HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl()))
                .timeout(Duration.ofSeconds(Math.max(5, config.getTimeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
    }

    private List<Integer> parseIndices(String responseBody, int documentCount, int topN) {
        JSONObject json = JSONUtil.parseObj(responseBody);
        JSONObject output = json.getJSONObject("output");
        JSONArray results = output == null ? null : output.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            throw new RagRerankUnavailableException("重排返回结构异常: " + truncate(responseBody));
        }
        List<Integer> indices = new ArrayList<>(results.size());
        for (int i = 0; i < results.size() && indices.size() < topN; i++) {
            Integer index = results.getJSONObject(i).getInt("index");
            if (index == null || index < 0 || index >= documentCount) {
                continue;
            }
            if (!indices.contains(index)) {
                indices.add(index);
            }
        }
        return indices;
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

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}

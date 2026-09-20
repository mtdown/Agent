package com.et.cloud.rag;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Primary
@Slf4j
public class LlmRagQueryExpansionClient implements RagQueryExpansionClient {

    private static final String SYSTEM_PROMPT = "You generate retrieval queries only. "
            + "Return compact JSON with keys rewrittenQuestion and hypotheticalAnswer. "
            + "Do not add facts not implied by the question.";

    private final RagProperties ragProperties;

    private final HttpClient httpClient;

    public LlmRagQueryExpansionClient(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public RagQueryExpansion expand(String query) {
        RagProperties.MultiQuery config = ragProperties.getRetrieval().getMultiQuery();
        if (config == null || !config.isEnabled() || !ragProperties.getLlm().isConfigured()) {
            return new RagQueryExpansion();
        }
        try {
            JSONObject body = new JSONObject();
            body.set("model", StrUtil.blankToDefault(config.getModel(), ragProperties.getLlm().getFastModel()));
            body.set("stream", false);
            body.set("temperature", 0);
            body.set("enable_thinking", false);
            body.set("max_tokens", Math.max(128, config.getMaxTokens()));
            JSONArray messages = new JSONArray();
            messages.add(new JSONObject().set("role", "system").set("content", SYSTEM_PROMPT));
            messages.add(new JSONObject().set("role", "user").set("content",
                    "Question:\n" + StrUtil.nullToEmpty(query)
                            + "\n\nReturn JSON only. The rewrittenQuestion should be one search question. "
                            + "The hypotheticalAnswer should be one concise possible answer for retrieval."));
            body.set("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(ragProperties.getLlm().getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(5, config.getTimeoutSeconds())))
                    .header("Authorization", "Bearer " + ragProperties.getLlm().getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("RAG query expansion failed HTTP {}", response.statusCode());
                return new RagQueryExpansion();
            }
            JSONObject json = JSONUtil.parseObj(response.body());
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return new RagQueryExpansion();
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            String content = message == null ? "" : message.getStr("content", "");
            return parseExpansion(content);
        } catch (Exception e) {
            log.warn("RAG query expansion failed, fallback to original query: {}", e.toString());
            return new RagQueryExpansion();
        }
    }

    private static RagQueryExpansion parseExpansion(String content) {
        if (StrUtil.isBlank(content)) {
            return new RagQueryExpansion();
        }
        String trimmed = content.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }
        JSONObject json = JSONUtil.parseObj(trimmed);
        return new RagQueryExpansion(
                json.getStr("rewrittenQuestion", ""),
                json.getStr("hypotheticalAnswer", ""));
    }

    private static String trimTrailingSlash(String url) {
        return url == null ? "" : (url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
    }
}

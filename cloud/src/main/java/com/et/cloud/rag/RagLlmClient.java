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

/**
 * OpenAI-compatible streaming chat client (default DeepSeek). Emits content and
 * reasoning deltas through {@link StreamCallback}; callers own the SSE emitter.
 */
@Component
@Slf4j
public class RagLlmClient {

    /**
     * Receives streamed deltas. Exactly one of onFinished/onError is invoked last.
     */
    public interface StreamCallback {
        void onReasoningDelta(String text);

        void onContentDelta(String text);

        void onFinished(String finishReason, Usage usage);

        void onError(String message);
    }

    /** Token usage reported by the provider on the final stream chunk (may be null). */
    public static class Usage {
        public final long promptTokens;
        public final long completionTokens;

        public Usage(long promptTokens, long completionTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }
    }

    private final RagProperties ragProperties;

    private final HttpClient httpClient;

    public RagLlmClient(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return ragProperties.getLlm().isConfigured();
    }

    /**
     * Streams one chat completion. Blocks until the stream ends, fails, or times out.
     *
     * <p>Transient upstream failures (e.g. connection reset) that happen BEFORE any
     * output was emitted are retried once automatically; failures after partial
     * output are surfaced immediately to avoid duplicated content.</p>
     *
     * @param deepThinking true = thinking model, false = fast model
     * @param prompt user prompt carrying the numbered reference materials
     */
    public void streamChat(String systemPrompt, String prompt, boolean deepThinking, StreamCallback callback) {
        RagProperties.Llm config = ragProperties.getLlm();
        if (!config.isConfigured()) {
            callback.onError("LLM 未配置（RAG_LLM_API_KEY）");
            return;
        }
        EmittingCallback first = new EmittingCallback(callback);
        int status = doStream(config, systemPrompt, prompt, deepThinking, first);
        if (status == INTERRUPTED) {
            callback.onError("LLM 流式响应被中断");
            return;
        }
        if (status == OK) {
            return;
        }
        if (first.emitted) {
            // partial output already streamed to the caller; retrying would duplicate it
            callback.onError(first.error);
            return;
        }
        log.warn("LLM 首次请求失败且未输出内容（{}），自动重试一次", first.error);
        EmittingCallback second = new EmittingCallback(callback);
        int retryStatus = doStream(config, systemPrompt, prompt, deepThinking, second);
        if (retryStatus == INTERRUPTED) {
            callback.onError("LLM 流式响应被中断");
            return;
        }
        if (retryStatus == FAILED) {
            callback.onError(second.error);
        }
    }

    private static final int OK = 0;
    private static final int INTERRUPTED = 1;
    private static final int FAILED = 2;

    /**
     * Runs one streaming attempt. Deltas and the final onFinished are delivered
     * through the callback; error/interruption outcomes are RETURNED, not
     * delivered, so the caller can decide whether to retry.
     */
    private int doStream(RagProperties.Llm config, String systemPrompt, String prompt,
                         boolean deepThinking, EmittingCallback cb) {
        JSONObject body = new JSONObject();
        body.set("model", config.resolveModel(deepThinking));
        body.set("stream", true);
        body.set("max_tokens", config.getMaxTokens());
        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().set("role", "system").set("content", systemPrompt));
        messages.add(new JSONObject().set("role", "user").set("content", prompt));
        body.set("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(config.getBaseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(Math.max(30, config.getTimeoutSeconds())))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                // non-stream error body: join lines for the message
                String errText = String.join("\n", response.body().toList());
                cb.error = "LLM 响应异常 HTTP " + response.statusCode() + ": " + truncate(errText);
                return FAILED;
            }
            String[] finishReason = {null};
            Usage[] usage = {null};
            boolean[] sawDone = {false};
            try {
                response.body().forEach(line -> parseLine(line, cb, finishReason, usage, sawDone));
            } catch (java.util.concurrent.CancellationException e) {
                return INTERRUPTED;
            }
            if (finishReason[0] == null && !sawDone[0]) {
                // stream ended without finish_reason or [DONE]: upstream truncated it
                cb.error = "LLM 流式响应异常截断（未收到结束标记）";
                return FAILED;
            }
            if (finishReason[0] == null) {
                finishReason[0] = "stop";
            }
            cb.onFinished(finishReason[0], usage[0]);
            return OK;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return INTERRUPTED;
        } catch (Exception e) {
            cb.error = "LLM 请求失败: " + e.getMessage();
            return FAILED;
        }
    }

    /** Forwards deltas immediately and tracks whether anything was emitted (retry-safety). */
    private static final class EmittingCallback implements StreamCallback {
        private final StreamCallback delegate;
        boolean emitted;
        String error;

        EmittingCallback(StreamCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onReasoningDelta(String text) {
            emitted = true;
            delegate.onReasoningDelta(text);
        }

        @Override
        public void onContentDelta(String text) {
            emitted = true;
            delegate.onContentDelta(text);
        }

        @Override
        public void onFinished(String finishReason, Usage usage) {
            delegate.onFinished(finishReason, usage);
        }

        @Override
        public void onError(String message) {
            delegate.onError(message);
        }
    }

    private void parseLine(String line, StreamCallback callback, String[] finishReason, Usage[] usage,
                           boolean[] sawDone) {
        if (line == null || line.isEmpty()) {
            return;
        }
        if (!line.startsWith("data:")) {
            return;
        }
        String payload = line.substring(5).trim();
        if ("[DONE]".equals(payload)) {
            sawDone[0] = true;
            return;
        }
        try {
            JSONObject json = JSONUtil.parseObj(payload);
            JSONObject usageJson = json.getJSONObject("usage");
            if (usageJson != null) {
                usage[0] = new Usage(
                        usageJson.getLong("prompt_tokens", 0L),
                        usageJson.getLong("completion_tokens", 0L));
            }
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return;
            }
            JSONObject choice = choices.getJSONObject(0);
            String fr = choice.getStr("finish_reason");
            if (fr != null) {
                finishReason[0] = fr;
            }
            JSONObject delta = choice.getJSONObject("delta");
            if (delta == null) {
                return;
            }
            String reasoning = delta.getStr("reasoning_content");
            if (reasoning != null && !reasoning.isEmpty()) {
                callback.onReasoningDelta(reasoning);
            }
            String content = delta.getStr("content");
            if (content != null && !content.isEmpty()) {
                callback.onContentDelta(content);
            }
        } catch (Exception e) {
            log.warn("LLM 流式行解析失败，跳过: {}", truncate(line));
        }
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

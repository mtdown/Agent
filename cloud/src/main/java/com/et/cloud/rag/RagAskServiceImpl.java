package com.et.cloud.rag;

import cn.hutool.core.util.StrUtil;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates one streamed ask: permission-filtered retrieval, prompt build,
 * LLM streaming, event emission. All chunk metadata flows to the client via the
 * meta event; the LLM only contributes [n] markers and prose.
 *
 * <p>The event flow is abstracted behind {@link EventSink} so the orchestration
 * is unit-testable without a live HTTP connection; {@link #ask} adapts the sink
 * onto an {@link SseEmitter}.</p>
 */
@Service
@Slf4j
public class RagAskServiceImpl implements RagAskService {

    /** Named event channel: meta / reason / delta / done / error. */
    interface EventSink {
        void emit(String name, Object payload);
    }

    static final String SYSTEM_PROMPT =
            "你是政策问答助手。只能依据提供的资料回答：\n"
                    + "1) 资料不足以回答时，明确说明\"根据现有资料未能找到相关依据\"，不得编造内容\n"
                    + "2) 引用资料时在相关句末标注角标[n]，n 为资料编号，可多条如[1][3]\n"
                    + "3) 不得自行编造文号；文号只能出现在资料标注中\n"
                    + "4) 回答使用简体中文，条理清晰";

    private static final String NO_HIT_REPLY =
            "根据现有检索范围（%d 个空间 / %d 篇授权文档）未能找到与该问题相关的资料，请调整问题表述或扩大检索范围。";

    @Resource
    private RagSearchService ragSearchService;

    @Resource
    private RagLlmClient ragLlmClient;

    @Override
    public SseEmitter ask(User loginUser, RagAskRequest request) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(request == null || StrUtil.isBlank(request.getQuery()),
                ErrorCode.PARAMS_ERROR, "查询内容不能为空");

        SseEmitter emitter = new SseEmitter(120_000L);
        java.util.concurrent.CompletableFuture<Void> future =
                CompletableFuture.runAsync(() -> runAsk(loginUser, request, sseSink(emitter)));
        // client disconnect / completion / timeout -> interrupt the still-streaming LLM call
        Runnable cancelTask = () -> future.cancel(true);
        emitter.onCompletion(cancelTask);
        emitter.onTimeout(cancelTask);
        return emitter;
    }

    private static EventSink sseSink(SseEmitter emitter) {
        return (name, payload) -> {
            try {
                emitter.send(SseEmitter.event().name(name).data(payload));
            } catch (Exception e) {
                // client disconnected or emitter already completed; stop caring, later sends fail the same way
                log.debug("SSE send failed (client likely disconnected): {}", e.getMessage());
            }
        };
    }

    /** Full ask flow against any sink. Sends terminal done/error itself. */
    void runAsk(User loginUser, RagAskRequest request, EventSink sink) {
        try {
            // 1. permission-filtered retrieval (hard filter inside RagSearchService)
            RagSearchRequest searchRequest = new RagSearchRequest();
            searchRequest.setQuery(request.getQuery());
            searchRequest.setSpaceIds(request.getSpaceIds());
            searchRequest.setTopK(request.getTopK());
            RagSearchResult search = ragSearchService.search(loginUser, searchRequest);
            List<ChunkHit> hits = search.getHits() == null ? new ArrayList<>() : search.getHits();
            Set<Long> effectiveSpaces = search.getEffectiveSpaceIds() == null
                    ? new LinkedHashSet<>() : search.getEffectiveSpaceIds();
            long docCount = search.getAuthorizedDocCount();

            // 2. meta first: scope + citations (LLM markers map onto these)
            List<RagCitation> citations = new ArrayList<>();
            StringBuilder materials = new StringBuilder();
            for (int i = 0; i < hits.size(); i++) {
                ChunkHit hit = hits.get(i);
                RagCitation citation = new RagCitation();
                citation.setIndex(i + 1);
                citation.setDocId(hit.getDocId());
                citation.setDocTitle(hit.getDocTitle());
                citation.setDocNumber(hit.getDocNumber());
                citation.setChunkIndex(hit.getChunkIndex());
                citation.setChunkHeading(hit.getChunkHeading());
                citation.setChunkText(hit.getChunkText());
                citations.add(citation);

                materials.append('[').append(i + 1).append("] 《")
                        .append(StrUtil.nullToEmpty(hit.getDocTitle())).append("》（")
                        .append(StrUtil.isBlank(hit.getDocNumber()) ? "无文号" : hit.getDocNumber())
                        .append("）\n")
                        .append(StrUtil.nullToEmpty(hit.getChunkText()))
                        .append("\n---\n");
            }
            sink.emit("meta", new MetaPayload(effectiveSpaces, docCount, citations));

            // 3. zero-hit short circuit: refuse without calling the LLM
            if (hits.isEmpty()) {
                sink.emit("delta", new DeltaPayload(
                        String.format(NO_HIT_REPLY, effectiveSpaces.size(), docCount)));
                sink.emit("done", new DonePayload("stop", 0L, 0L, null));
                return;
            }

            // 4. stream the LLM answer (timing: thinking = until first content delta)
            boolean deepThinking = Boolean.TRUE.equals(request.getDeepThinking());
            String prompt = "问题：" + request.getQuery().trim() + "\n\n资料：\n" + materials;
            long llmStart = System.currentTimeMillis();
            long[] firstContentAt = {0L};
            ragLlmClient.streamChat(SYSTEM_PROMPT, prompt, deepThinking, new RagLlmClient.StreamCallback() {
                @Override
                public void onReasoningDelta(String text) {
                    sink.emit("reason", new DeltaPayload(text));
                }

                @Override
                public void onContentDelta(String text) {
                    if (firstContentAt[0] == 0L) {
                        firstContentAt[0] = System.currentTimeMillis();
                    }
                    sink.emit("delta", new DeltaPayload(text));
                }

                @Override
                public void onFinished(String finishReason, RagLlmClient.Usage usage) {
                    long now = System.currentTimeMillis();
                    long thinkingMs = firstContentAt[0] == 0L
                            ? now - llmStart : firstContentAt[0] - llmStart;
                    long answerMs = firstContentAt[0] == 0L
                            ? 0L : now - firstContentAt[0];
                    sink.emit("done", new DonePayload(finishReason, thinkingMs, answerMs,
                            usage == null ? null : new DonePayload.Usage(
                                    usage.promptTokens, usage.completionTokens)));
                }

                @Override
                public void onError(String message) {
                    log.error("RAG ask LLM error: {}", message);
                    sink.emit("error", new ErrorPayload(message));
                }
            });
        } catch (Exception e) {
            log.error("RAG ask failed", e);
            sink.emit("error", new ErrorPayload(
                    e.getMessage() == null ? "问答服务异常" : e.getMessage()));
        }
    }

    // ---- SSE payloads (public fields: serialized as-is by Jackson) ----

    public static class MetaPayload {
        public Set<Long> effectiveSpaceIds;
        public long authorizedDocCount;
        public List<RagCitation> citations;

        public MetaPayload(Set<Long> effectiveSpaceIds, long authorizedDocCount, List<RagCitation> citations) {
            this.effectiveSpaceIds = effectiveSpaceIds;
            this.authorizedDocCount = authorizedDocCount;
            this.citations = citations;
        }
    }

    public static class DeltaPayload {
        public String content;

        public DeltaPayload(String content) {
            this.content = content;
        }
    }

    public static class DonePayload {
        public String finishReason;
        public Long thinkingMs;
        public Long answerMs;
        public Usage usage;

        public DonePayload(String finishReason, Long thinkingMs, Long answerMs, Usage usage) {
            this.finishReason = finishReason;
            this.thinkingMs = thinkingMs;
            this.answerMs = answerMs;
            this.usage = usage;
        }

        /** Provider-reported token usage (null when the stream aborted early). */
        public static class Usage {
            public long promptTokens;
            public long completionTokens;

            public Usage(long promptTokens, long completionTokens) {
                this.promptTokens = promptTokens;
                this.completionTokens = completionTokens;
            }
        }
    }

    public static class ErrorPayload {
        public String message;

        public ErrorPayload(String message) {
            this.message = message;
        }
    }
}

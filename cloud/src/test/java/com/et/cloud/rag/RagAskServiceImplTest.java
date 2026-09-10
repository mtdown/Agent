package com.et.cloud.rag;

import com.et.cloud.exception.BusinessException;
import com.et.cloud.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The ask pipeline must (a) refuse blank/unauthenticated calls before SSE starts,
 * (b) emit meta with citations BEFORE any LLM text, (c) short-circuit zero-hit
 * questions without touching the LLM, and (d) relay LLM errors as error events.
 */
class RagAskServiceImplTest {

    private RagSearchService ragSearchService;
    private RagLlmClient ragLlmClient;
    private RagAskServiceImpl ragAskService;

    @BeforeEach
    void setUp() {
        ragSearchService = mock(RagSearchService.class);
        ragLlmClient = mock(RagLlmClient.class);
        ragAskService = new RagAskServiceImpl();
        ReflectionTestUtils.setField(ragAskService, "ragSearchService", ragSearchService);
        ReflectionTestUtils.setField(ragAskService, "ragLlmClient", ragLlmClient);
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        return user;
    }

    private RagSearchResult searchResult(ChunkHit... hits) {
        RagSearchResult result = new RagSearchResult();
        result.setEffectiveSpaceIds(new java.util.LinkedHashSet<>(Set.of(10L)));
        result.setAuthorizedDocCount(3L);
        result.setHits(List.of(hits));
        return result;
    }

    private ChunkHit hit(long docId, String title, String docNumber, String text) {
        return new ChunkHit(docId, docId, 10L, 0, "标题路径", text, title, docNumber, 0.9d);
    }

    private RagAskRequest request(String query) {
        RagAskRequest request = new RagAskRequest();
        request.setQuery(query);
        return request;
    }

    /** Recording sink: captures (name, payload) pairs in order. */
    private static class RecordingSink implements RagAskServiceImpl.EventSink {
        final List<String> names = new ArrayList<>();
        final List<Object> payloads = new ArrayList<>();

        @Override
        public void emit(String name, Object payload) {
            names.add(name);
            payloads.add(payload);
        }

        RagAskServiceImpl.MetaPayload meta() {
            for (Object p : payloads) {
                if (p instanceof RagAskServiceImpl.MetaPayload) {
                    return (RagAskServiceImpl.MetaPayload) p;
                }
            }
            return null;
        }
    }

    @Test
    void blankQueryOrAnonymousThrowsBeforeSse() {
        assertThrows(BusinessException.class, () -> ragAskService.ask(null, request("问题")));
        assertThrows(BusinessException.class, () -> ragAskService.ask(user(), request(" ")));
        assertThrows(BusinessException.class, () -> ragAskService.ask(user(), null));
    }

    @Test
    void zeroHitsShortCircuitsWithoutLlm() {
        when(ragSearchService.search(any(User.class), any(RagSearchRequest.class)))
                .thenReturn(searchResult());

        RecordingSink sink = new RecordingSink();
        ragAskService.runAsk(user(), request("不存在的问题"), sink);

        verify(ragLlmClient, never()).streamChat(anyString(), anyString(), anyBoolean(), any());
        assertEquals(List.of("meta", "delta", "done"), sink.names);
        RagAskServiceImpl.DeltaPayload reply =
                (RagAskServiceImpl.DeltaPayload) sink.payloads.get(1);
        assertTrue(reply.content.contains("未能找到"));
        assertTrue(reply.content.contains("1 个空间"));
    }

    @Test
    void metaCarriesCitationsInHitOrderAndPromptNumbersMatch() {
        when(ragSearchService.search(any(User.class), any(RagSearchRequest.class)))
                .thenReturn(searchResult(
                        hit(1L, "文档甲", "渝府办发〔2026〕24号", "甲正文"),
                        hit(2L, "文档乙", null, "乙正文")));

        java.util.concurrent.atomic.AtomicReference<String> capturedPrompt = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(invocation -> {
            capturedPrompt.set(invocation.getArgument(1));
            RagLlmClient.StreamCallback callback = invocation.getArgument(3);
            callback.onReasoningDelta("思考片段");
            callback.onContentDelta("答案[1]");
            callback.onFinished("stop", new RagLlmClient.Usage(100L, 50L));
            return null;
        }).when(ragLlmClient).streamChat(anyString(), anyString(), anyBoolean(), any());

        RecordingSink sink = new RecordingSink();
        ragAskService.runAsk(user(), request("某政策问题"), sink);

        // meta first, then reasoning, delta, done
        assertEquals(List.of("meta", "reason", "delta", "done"), sink.names);
        RagAskServiceImpl.MetaPayload meta = sink.meta();
        assertEquals(2, meta.citations.size());
        assertEquals(1, meta.citations.get(0).getIndex());
        assertEquals("文档甲", meta.citations.get(0).getDocTitle());
        assertEquals("渝府办发〔2026〕24号", meta.citations.get(0).getDocNumber());
        // prompt numbering matches citation order; blank docNumber renders as 无文号
        String prompt = capturedPrompt.get();
        assertTrue(prompt.contains("[1] 《文档甲》（渝府办发〔2026〕24号）"));
        assertTrue(prompt.contains("[2] 《文档乙》（无文号）"));
        assertTrue(prompt.contains("问题：某政策问题"));
        // system prompt passed through
        verify(ragLlmClient).streamChat(eq(RagAskServiceImpl.SYSTEM_PROMPT), anyString(), anyBoolean(), any());
    }

    @Test
    void llmErrorIsRelayedAsErrorEvent() {
        when(ragSearchService.search(any(User.class), any(RagSearchRequest.class)))
                .thenReturn(searchResult(hit(1L, "文档甲", "文号X", "正文")));
        doAnswer(invocation -> {
            RagLlmClient.StreamCallback callback = invocation.getArgument(3);
            callback.onError("LLM 未配置（RAG_LLM_API_KEY）");
            return null;
        }).when(ragLlmClient).streamChat(anyString(), anyString(), anyBoolean(), any());

        RecordingSink sink = new RecordingSink();
        ragAskService.runAsk(user(), request("问题"), sink);

        assertEquals("meta", sink.names.get(0));
        assertEquals("error", sink.names.get(sink.names.size() - 1));
        RagAskServiceImpl.ErrorPayload error = (RagAskServiceImpl.ErrorPayload)
                sink.payloads.get(sink.payloads.size() - 1);
        assertTrue(error.message.contains("RAG_LLM_API_KEY"));
    }

    @Test
    void retrievalFailureBecomesErrorEventNotException() {
        when(ragSearchService.search(any(User.class), any(RagSearchRequest.class)))
                .thenThrow(new RuntimeException("embedding down"));

        RecordingSink sink = new RecordingSink();
        ragAskService.runAsk(user(), request("问题"), sink);

        assertEquals(List.of("error"), sink.names);
        assertTrue(((RagAskServiceImpl.ErrorPayload) sink.payloads.get(0)).message.contains("embedding down"));
    }
}

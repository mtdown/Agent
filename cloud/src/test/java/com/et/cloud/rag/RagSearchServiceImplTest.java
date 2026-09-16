package com.et.cloud.rag;

import com.et.cloud.exception.BusinessException;
import com.et.cloud.mapper.WikiChunkMapper;
import com.et.cloud.model.entity.User;
import com.et.cloud.service.WikiSpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Permission filtering is the security boundary of the whole RAG feature:
 * the vector store must only ever see the effective (visible ∩ requested)
 * space set, and an empty intersection must short-circuit.
 *
 * <p>The second half of this class locks down the ranking pipeline. Two
 * behaviours that used to be asserted here were in fact defects and are now
 * asserted in the OPPOSITE direction:
 * <ul>
 *   <li>a saturated doc-number layer used to skip embedding and vector search
 *       entirely — the exact layer is now a pinned group that runs alongside
 *       the semantic channels, never instead of them;</li>
 *   <li>the vector store used to be queried with exactly topK — it is now
 *       queried with a deliberately deeper candidate pool, because a perfect
 *       re-ranker cannot recover a gold chunk that the pool never contained.</li>
 * </ul>
 */
class RagSearchServiceImplTest {

    private WikiSpaceService wikiSpaceService;
    private WikiChunkMapper wikiChunkMapper;
    private RagEmbeddingClient embeddingClient;
    private VectorStore vectorStore;
    private LexicalIndex lexicalIndex;
    private RagRerankClient rerankClient;
    private RagProperties properties;
    private RagSearchServiceImpl ragSearchService;

    @BeforeEach
    void setUp() {
        wikiSpaceService = mock(WikiSpaceService.class);
        wikiChunkMapper = mock(WikiChunkMapper.class);
        embeddingClient = mock(RagEmbeddingClient.class);
        vectorStore = mock(VectorStore.class);
        lexicalIndex = mock(LexicalIndex.class);
        rerankClient = mock(RagRerankClient.class);
        properties = new RagProperties();
        ragSearchService = new RagSearchServiceImpl();
        ReflectionTestUtils.setField(ragSearchService, "wikiSpaceService", wikiSpaceService);
        ReflectionTestUtils.setField(ragSearchService, "wikiChunkMapper", wikiChunkMapper);
        ReflectionTestUtils.setField(ragSearchService, "ragEmbeddingClient", embeddingClient);
        ReflectionTestUtils.setField(ragSearchService, "vectorStore", vectorStore);
        ReflectionTestUtils.setField(ragSearchService, "lexicalIndex", lexicalIndex);
        ReflectionTestUtils.setField(ragSearchService, "ragRerankClient", rerankClient);
        ReflectionTestUtils.setField(ragSearchService, "ragProperties", properties);
        // default: a configured embedding endpoint. Individual tests override this
        // to exercise the degraded local-only path.
        when(embeddingClient.isConfigured()).thenReturn(true);
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));
    }

    /** Turns re-ranking on for a single test by supplying a key. */
    private void enableRerank() {
        properties.getRetrieval().getRerank().setApiKey("test-rerank-key");
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        return user;
    }

    /** A search request that carries no document number, so only the channels under test fire. */
    private RagSearchRequest plainRequest() {
        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("低保怎么申请");
        return request;
    }

    private RagSearchResult search(RagSearchRequest request) {
        return ragSearchService.search(user(), request);
    }

    private static ChunkHit hit(long chunkId, String text) {
        return new ChunkHit(chunkId, 20L, 1L, 0, null, text, "另一篇", null, 0.7d);
    }

    private static List<ChunkHit> hits(int count) {
        List<ChunkHit> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(hit(1000L + i, "语义块" + i));
        }
        return out;
    }

    // ---------------------------------------------------------------- permission

    @Test
    void scopeIntersectsVisibleSpaces() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L, 2L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L, 2L, 3L));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of());

        RagSearchRequest request = plainRequest();
        request.setSpaceIds(List.of(2L, 3L)); // 3 not visible
        RagSearchResult result = search(request);

        assertEquals(Set.of(2L), result.getEffectiveSpaceIds());
        assertEquals(3, result.getAuthorizedDocCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> spaceCaptor = ArgumentCaptor.forClass(Set.class);
        verify(vectorStore).search(any(float[].class), spaceCaptor.capture(), anyInt());
        assertEquals(Set.of(2L), spaceCaptor.getValue(), "vector store must only see the filtered set");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> lexicalCaptor = ArgumentCaptor.forClass(Set.class);
        verify(lexicalIndex).search(anyString(), lexicalCaptor.capture(), anyInt());
        assertEquals(Set.of(2L), lexicalCaptor.getValue(), "lexical channel must see the same filtered set");
    }

    @Test
    void scopeWithNoVisibleSpaceShortCircuits() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));

        RagSearchRequest request = plainRequest();
        request.setSpaceIds(List.of(99L)); // user cannot see 99
        RagSearchResult result = search(request);

        assertTrue(result.getHits().isEmpty());
        assertTrue(result.getEffectiveSpaceIds().isEmpty());
        verify(vectorStore, never()).search(any(float[].class), anySet(), anyInt());
        verify(lexicalIndex, never()).search(anyString(), anySet(), anyInt());
        verify(embeddingClient, never()).embed(anyList());
    }

    @Test
    void nullScopeSearchesAllVisibleSpaces() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L, 2L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(Collections.emptyList());
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of());

        RagSearchResult result = search(plainRequest());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> spaceCaptor = ArgumentCaptor.forClass(Set.class);
        verify(vectorStore).search(any(float[].class), spaceCaptor.capture(), anyInt());
        assertEquals(Set.of(1L, 2L), spaceCaptor.getValue());
        assertEquals(0, result.getAuthorizedDocCount());
    }

    @Test
    void effectiveSpaceIdsPreserveRequestedOrder() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(5L, 1L, 3L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(Collections.emptyList());
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of());

        RagSearchRequest request = plainRequest();
        request.setSpaceIds(List.of(3L, 5L, 99L));
        RagSearchResult result = search(request);

        assertEquals(new LinkedHashSet<>(List.of(3L, 5L)), result.getEffectiveSpaceIds());
    }

    @Test
    void blankQueryRejected() {
        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("  ");
        assertThrows(BusinessException.class, () -> ragSearchService.search(user(), request));
        RagSearchRequest nullRequest = new RagSearchRequest();
        nullRequest.setQuery("问题");
        assertThrows(BusinessException.class, () -> ragSearchService.search(user(), null));
        assertThrows(BusinessException.class, () -> ragSearchService.search(null, nullRequest));
    }

    // ------------------------------------------------------------ candidate pool

    @Test
    void candidatePoolIsDeeperThanTheAnswerSet() {
        // a gold chunk the pool never contained can never be recovered by ranking,
        // so the channels must be asked for more than topK candidates
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(hits(40));

        RagSearchRequest request = plainRequest();
        request.setTopK(6);
        RagSearchResult result = search(request);

        verify(vectorStore).search(any(float[].class), anySet(), eq(properties.getRetrieval().getCandidatePoolSize()));
        verify(lexicalIndex).search(anyString(), anySet(), eq(properties.getRetrieval().getCandidatePoolSize()));
        assertEquals(6, result.getHits().size(), "the returned answer set is still capped at topK");
    }

    @Test
    void requestedTopKIsCappedByConfiguration() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(hits(60));

        RagSearchRequest request = plainRequest();
        request.setTopK(500); // a single request must not be able to drain the whole corpus
        RagSearchResult result = search(request);

        int max = properties.getRetrieval().getTopKMax();
        assertEquals(max, result.getHits().size());
        // the pool is never allowed to be shallower than the answer set it has to fill
        verify(vectorStore).search(any(float[].class), anySet(),
                eq(Math.max(properties.getRetrieval().getCandidatePoolSize(), max)));
    }

    @Test
    void lexicalChannelAdmitsChunksTheDenseChannelMisses() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of());
        when(lexicalIndex.search(anyString(), anySet(), anyInt())).thenReturn(List.of(hit(301L, "低保申请条件")));

        RagSearchResult result = search(plainRequest());

        assertEquals(1, result.getHits().size());
        assertEquals(301L, result.getHits().get(0).getChunkId());
    }

    @Test
    void fusedRankingPrefersChunksBothChannelsAgreeOn() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L));
        // dense: A first, B second. lexical: B first, A second.
        // B is 1st+2nd, A is 1st+2nd too — but C only appears in one channel and
        // must never outrank a chunk that both channels retrieved.
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(
                List.of(hit(1L, "A"), hit(2L, "B"), hit(3L, "C")));
        when(lexicalIndex.search(anyString(), anySet(), anyInt())).thenReturn(
                List.of(hit(2L, "B"), hit(1L, "A")));

        RagSearchResult result = search(plainRequest());

        List<Long> ids = new ArrayList<>();
        for (ChunkHit h : result.getHits()) {
            ids.add(h.getChunkId());
        }
        assertEquals(List.of(1L, 2L, 3L), ids);
    }

    // ------------------------------------------------------------------ rerank

    @Test
    void rerankReordersTheFusedPool() {
        enableRerank();
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(
                List.of(hit(201L, "甲"), hit(202L, "乙"), hit(203L, "丙")));
        when(rerankClient.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of(2, 0, 1));

        RagSearchRequest request = plainRequest();
        request.setTopK(3);
        RagSearchResult result = search(request);

        List<Long> ids = new ArrayList<>();
        for (ChunkHit h : result.getHits()) {
            ids.add(h.getChunkId());
        }
        assertEquals(List.of(203L, 201L, 202L), ids);
        assertNotNull(result.getTimings().getRerankMs());
    }

    @Test
    void rerankInputCarriesTitleAndHeading() {
        // the re-ranker sees topical signal the chunk body alone does not carry:
        // 公文正文经常以"第一条"起头，标题才是唯一能区分主题的信息
        enableRerank();
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L));
        List<ChunkHit> pool = new ArrayList<>();
        pool.add(new ChunkHit(201L, 20L, 1L, 0, "第一章 总则", "第一条 为规范低保申请……", "重庆市最低生活保障办法", null, 0.7d));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(pool);
        when(rerankClient.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of(0));

        search(plainRequest());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(rerankClient).rerank(anyString(), docCaptor.capture(), anyInt());
        assertEquals("《重庆市最低生活保障办法》第一章 总则\n第一条 为规范低保申请……",
                docCaptor.getValue().get(0));
    }

    @Test
    void rerankIsNotCalledWhenNoKeyIsConfigured() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(hits(3));

        RagSearchResult result = search(plainRequest());

        verify(rerankClient, never()).rerank(anyString(), anyList(), anyInt());
        assertNull(result.getTimings().getRerankMs(), "an unexecuted phase stays null, not a misleading 0");
        assertEquals(3, result.getHits().size());
    }

    @Test
    void rerankFailureFallsBackToFusedOrderAndStillAnswers() {
        enableRerank();
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(
                List.of(hit(201L, "甲"), hit(202L, "乙")));
        when(rerankClient.rerank(anyString(), anyList(), anyInt()))
                .thenThrow(new RagRerankUnavailableException("endpoint down"));

        RagSearchResult result = search(plainRequest());

        List<Long> ids = new ArrayList<>();
        for (ChunkHit h : result.getHits()) {
            ids.add(h.getChunkId());
        }
        assertEquals(List.of(201L, 202L), ids, "a dead re-ranker must degrade, not fail the request");
        assertNotNull(result.getTimings().getRerankMs(), "the phase did run; its cost is recorded honestly");
    }

    @Test
    void docNumberGroupKeepsItsLeadingSlotsWhenRerankIsOn() {
        // regression guard: re-ranking the pinned group together with the rest lets
        // unrelated chunks displace it. Measured, that costs the doc-number layer
        // ~24pt of recall@6 — so the pinned group keeps its slots unconditionally.
        enableRerank();
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(10L));
        when(wikiChunkMapper.selectList(any())).thenReturn(
                List.of(chunk(100L, 10L, 0, "渝府办发〔2026〕24号"),
                        chunk(101L, 10L, 1, "渝府办发〔2026〕24号")));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of(hit(201L, "无关块")));
        // the re-ranker, given only the un-pinned remainder, wants nothing to do with it
        when(rerankClient.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of(0));

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("渝府办发〔2026〕24号的主要内容");
        RagSearchResult result = search(request);

        assertEquals(100L, result.getHits().get(0).getChunkId());
        assertEquals(101L, result.getHits().get(1).getChunkId());
        assertEquals(201L, result.getHits().get(2).getChunkId());
    }

    @Test
    void docNumberGroupNeverShrinksWhenTheRerankerReturnsFewerIndices() {
        // a re-ranker that answers with a shorter list than it was given must not
        // be allowed to drop exact doc-number chunks out of the pinned group
        enableRerank();
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(10L));
        when(wikiChunkMapper.selectList(any())).thenReturn(
                List.of(chunk(100L, 10L, 0, "渝府办发〔2026〕24号"),
                        chunk(101L, 10L, 1, "渝府办发〔2026〕24号"),
                        chunk(102L, 10L, 2, "渝府办发〔2026〕24号")));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of());
        // only one index comes back for a three-document group
        when(rerankClient.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of(2));

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("渝府办发〔2026〕24号的主要内容");
        RagSearchResult result = search(request);

        assertEquals(3, result.getHits().size(), "the pinned group lost two chunks: " + result.getHits());
        assertEquals(102L, result.getHits().get(0).getChunkId());
        assertEquals(100L, result.getHits().get(1).getChunkId());
        assertEquals(101L, result.getHits().get(2).getChunkId());
    }

    // ------------------------------------------------------------- doc number

    @Test
    void docNumberQueryHitsExactMatchFirst() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(10L));
        when(wikiChunkMapper.selectList(any())).thenReturn(
                List.of(chunk(100L, 10L, 0, "渝府办发〔2026〕24号"), chunk(101L, 10L, 1, "渝府办发〔2026〕24号")));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of(hit(200L, "语义块")));

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("渝府办发〔2026〕24号说了什么");
        RagSearchResult result = search(request);

        assertEquals(3, result.getHits().size());
        assertEquals(100L, result.getHits().get(0).getChunkId());
        assertEquals(1.0d, result.getHits().get(0).getScore(), 1e-9, "exact doc-number match scores 1.0");
        assertEquals(101L, result.getHits().get(1).getChunkId());
        assertEquals(200L, result.getHits().get(2).getChunkId(), "semantic hits fill the remaining slots");
    }

    @Test
    void docNumberLayerNoLongerSkipsTheSemanticChannels() {
        // regression: the exact layer used to short-circuit the pipeline as soon as
        // it filled topK, so a doc-number query never saw BM25 or vector recall.
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(10L));
        when(wikiChunkMapper.selectList(any())).thenReturn(
                List.of(chunk(100L, 10L, 0, "渝府办发〔2026〕24号"),
                        chunk(101L, 10L, 1, "渝府办发〔2026〕24号"),
                        chunk(102L, 10L, 2, "渝府办发〔2026〕24号")));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of());

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("渝府办发〔2026〕24号的主要内容");
        request.setTopK(3);
        RagSearchResult result = search(request);

        assertEquals(3, result.getHits().size());
        verify(embeddingClient).embed(anyList());
        verify(vectorStore).search(any(float[].class), anySet(), anyInt());
        verify(lexicalIndex).search(anyString(), anySet(), anyInt());
        assertNotNull(result.getTimings().getEmbedMs());
        assertNotNull(result.getTimings().getVectorMs());
        assertNotNull(result.getTimings().getLexicalMs());
    }

    @Test
    void docNumberQueryWithoutCorpusMatchFallsBackToTheChannels() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(10L));
        when(wikiChunkMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of(hit(200L, "语义块")));

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("国办发〔1999〕1号说了什么"); // 不在语料中的文号
        RagSearchResult result = search(request);

        assertEquals(1, result.getHits().size());
        assertEquals(200L, result.getHits().get(0).getChunkId());
    }

    // ------------------------------------------------------------- degradation

    @Test
    void lexicalOnlyDegradesGracefullyWhenEmbeddingIsUnconfigured() {
        when(embeddingClient.isConfigured()).thenReturn(false);
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L));
        when(lexicalIndex.search(anyString(), anySet(), anyInt())).thenReturn(List.of(hit(301L, "低保申请条件")));

        RagSearchResult result = search(plainRequest());

        assertEquals(1, result.getHits().size());
        assertEquals(301L, result.getHits().get(0).getChunkId());
        verify(vectorStore, never()).search(any(float[].class), anySet(), anyInt());
        assertNull(result.getTimings().getEmbedMs());
        assertNull(result.getTimings().getVectorMs());
    }

    @Test
    void lexicalChannelStaysOffWhenDisabledByConfiguration() {
        properties.getRetrieval().getLexical().setEnabled(false);
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of(hit(201L, "甲")));

        RagSearchResult result = search(plainRequest());

        verify(lexicalIndex, never()).search(anyString(), anySet(), anyInt());
        assertEquals(201L, result.getHits().get(0).getChunkId());
    }

    @Test
    void searchFillsPhaseTimings() {
        enableRerank();
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectList(any())).thenReturn(
                List.of(chunk(100L, 10L, 0, "渝府办发〔2026〕24号"), chunk(101L, 10L, 1, "渝府办发〔2026〕24号")));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of(hit(201L, "甲")));
        when(rerankClient.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of(0));

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("渝府办发〔2026〕24号的主要内容");
        SearchTimings t = search(request).getTimings();

        assertNotNull(t, "timings must always be present (null-safe for consumers)");
        assertNotNull(t.getPermissionMs());
        assertNotNull(t.getDocCountMs());
        assertNotNull(t.getDocNumberMs());
        assertNotNull(t.getEmbedMs());
        assertNotNull(t.getVectorMs());
        assertNotNull(t.getLexicalMs());
        assertNotNull(t.getFusionMs());
        assertNotNull(t.getRerankMs());
        assertNotNull(t.getTotalMs());
    }

    private com.et.cloud.model.entity.WikiChunk chunk(long id, long docId, int index, String docNumber) {
        com.et.cloud.model.entity.WikiChunk chunk = new com.et.cloud.model.entity.WikiChunk();
        chunk.setId(id);
        chunk.setDocId(docId);
        chunk.setSpaceId(1L);
        chunk.setChunkIndex(index);
        chunk.setChunkText("正文" + index);
        chunk.setDocTitle("标题");
        chunk.setDocNumber(docNumber);
        chunk.setStatus(com.et.cloud.model.entity.WikiChunk.STATUS_ACTIVE);
        return chunk;
    }
}

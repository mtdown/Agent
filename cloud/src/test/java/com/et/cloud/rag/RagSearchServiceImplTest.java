package com.et.cloud.rag;

import com.et.cloud.exception.BusinessException;
import com.et.cloud.mapper.WikiChunkMapper;
import com.et.cloud.model.entity.User;
import com.et.cloud.service.WikiSpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Permission filtering is the security boundary of the whole RAG feature:
 * the vector store must only ever see the effective (visible ∩ requested)
 * space set, and an empty intersection must short-circuit.
 */
class RagSearchServiceImplTest {

    private WikiSpaceService wikiSpaceService;
    private WikiChunkMapper wikiChunkMapper;
    private RagEmbeddingClient embeddingClient;
    private VectorStore vectorStore;
    private RagSearchServiceImpl ragSearchService;

    @BeforeEach
    void setUp() {
        wikiSpaceService = mock(WikiSpaceService.class);
        wikiChunkMapper = mock(WikiChunkMapper.class);
        embeddingClient = mock(RagEmbeddingClient.class);
        vectorStore = mock(VectorStore.class);
        RagProperties properties = new RagProperties();
        ragSearchService = new RagSearchServiceImpl();
        ReflectionTestUtils.setField(ragSearchService, "wikiSpaceService", wikiSpaceService);
        ReflectionTestUtils.setField(ragSearchService, "wikiChunkMapper", wikiChunkMapper);
        ReflectionTestUtils.setField(ragSearchService, "ragEmbeddingClient", embeddingClient);
        ReflectionTestUtils.setField(ragSearchService, "vectorStore", vectorStore);
        ReflectionTestUtils.setField(ragSearchService, "ragProperties", properties);
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        return user;
    }

    @Test
    void scopeIntersectsVisibleSpaces() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L, 2L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(1L, 2L, 3L));
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of());

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("低保怎么申请");
        request.setSpaceIds(List.of(2L, 3L)); // 3 not visible
        RagSearchResult result = ragSearchService.search(user(), request);

        assertEquals(Set.of(2L), result.getEffectiveSpaceIds());
        assertEquals(3, result.getAuthorizedDocCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> spaceCaptor = ArgumentCaptor.forClass(Set.class);
        verify(vectorStore).search(any(float[].class), spaceCaptor.capture(), anyInt());
        assertEquals(Set.of(2L), spaceCaptor.getValue(), "vector store must only see the filtered set");
    }

    @Test
    void scopeWithNoVisibleSpaceShortCircuits() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("问题");
        request.setSpaceIds(List.of(99L)); // user cannot see 99
        RagSearchResult result = ragSearchService.search(user(), request);

        assertTrue(result.getHits().isEmpty());
        assertTrue(result.getEffectiveSpaceIds().isEmpty());
        verify(vectorStore, never()).search(any(float[].class), anySet(), anyInt());
        verify(embeddingClient, never()).embed(anyList());
    }

    @Test
    void nullScopeSearchesAllVisibleSpaces() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L, 2L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(Collections.emptyList());
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of());

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("问题");
        ragSearchService.search(user(), request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> spaceCaptor = ArgumentCaptor.forClass(Set.class);
        verify(vectorStore).search(any(float[].class), spaceCaptor.capture(), anyInt());
        assertEquals(Set.of(1L, 2L), spaceCaptor.getValue());
        assertEquals(0, ragSearchService.search(user(), request).getAuthorizedDocCount());
    }

    @Test
    void defaultTopKFallsBackToConfig() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(Collections.emptyList());
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of());

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("问题");
        ragSearchService.search(user(), request);

        verify(vectorStore).search(any(float[].class), anySet(), org.mockito.ArgumentMatchers.eq(6));
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

    @Test
    void effectiveSpaceIdsPreserveRequestedOrder() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(5L, 1L, 3L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(Collections.emptyList());
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of());

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("问题");
        request.setSpaceIds(List.of(3L, 5L, 99L));
        RagSearchResult result = ragSearchService.search(user(), request);

        assertEquals(new LinkedHashSet<>(List.of(3L, 5L)), result.getEffectiveSpaceIds());
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

    @Test
    void docNumberQueryHitsExactMatchFirst() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(10L));
        when(wikiChunkMapper.selectList(any())).thenReturn(
                List.of(chunk(100L, 10L, 0, "渝府办发〔2026〕24号"), chunk(101L, 10L, 1, "渝府办发〔2026〕24号")));
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));
        ChunkHit vectorHit = new ChunkHit(200L, 20L, 1L, 0, "", "语义块", "另一篇", null, 0.7d);
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of(vectorHit));

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("渝府办发〔2026〕24号说了什么");
        RagSearchResult result = ragSearchService.search(user(), request);

        assertEquals(3, result.getHits().size());
        assertEquals(100L, result.getHits().get(0).getChunkId());
        assertEquals(1.0d, result.getHits().get(0).getScore(), 1e-9, "exact doc-number match scores 1.0");
        assertEquals(101L, result.getHits().get(1).getChunkId());
        assertEquals(200L, result.getHits().get(2).getChunkId(), "vector hits fill remaining slots");
    }

    @Test
    void docNumberExactHitsSaturatingTopKSkipsVectorSearch() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(10L));
        when(wikiChunkMapper.selectList(any())).thenReturn(
                List.of(chunk(100L, 10L, 0, "渝府办发〔2026〕24号"),
                        chunk(101L, 10L, 1, "渝府办发〔2026〕24号"),
                        chunk(102L, 10L, 2, "渝府办发〔2026〕24号")));

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("渝府办发〔2026〕24号的主要内容");
        request.setTopK(3);
        RagSearchResult result = ragSearchService.search(user(), request);

        assertEquals(3, result.getHits().size());
        verify(embeddingClient, never()).embed(anyList());
        verify(vectorStore, never()).search(any(float[].class), anySet(), anyInt());
    }

    @Test
    void docNumberQueryWithoutCorpusMatchFallsBackToVector() {
        when(wikiSpaceService.listVisibleSpaceIds(any(User.class))).thenReturn(List.of(1L));
        when(wikiChunkMapper.selectObjs(any())).thenReturn(List.of(10L));
        when(wikiChunkMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));
        ChunkHit vectorHit = new ChunkHit(200L, 20L, 1L, 0, "", "语义块", "另一篇", null, 0.7d);
        when(vectorStore.search(any(float[].class), anySet(), anyInt())).thenReturn(List.of(vectorHit));

        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("国办发〔1999〕1号说了什么"); // 不在语料中的文号
        RagSearchResult result = ragSearchService.search(user(), request);

        assertEquals(1, result.getHits().size());
        assertEquals(200L, result.getHits().get(0).getChunkId());
    }
}

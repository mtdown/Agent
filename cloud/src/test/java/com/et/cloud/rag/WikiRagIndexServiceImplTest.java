package com.et.cloud.rag;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.et.cloud.mapper.DocumentWikiMapper;
import com.et.cloud.mapper.WikiChunkMapper;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.WikiChunk;
import com.et.cloud.service.WikiChunkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Index lifecycle behaviour: build / invalidate / reactivate / degrade on
 * embedding failure / idempotent backfill / orphan reconciliation.
 */
class WikiRagIndexServiceImplTest {

    private DocumentWikiMapper documentWikiMapper;
    private WikiChunkMapper wikiChunkMapper;
    private WikiChunkService wikiChunkService;
    private RagEmbeddingClient embeddingClient;
    private VectorStore vectorStore;
    private WikiRagIndexServiceImpl indexService;

    @BeforeEach
    void setUp() {
        documentWikiMapper = mock(DocumentWikiMapper.class);
        wikiChunkMapper = mock(WikiChunkMapper.class);
        wikiChunkService = mock(WikiChunkService.class);
        embeddingClient = mock(RagEmbeddingClient.class);
        vectorStore = mock(VectorStore.class);
        RagProperties properties = new RagProperties();
        indexService = new WikiRagIndexServiceImpl();
        ReflectionTestUtils.setField(indexService, "documentWikiMapper", documentWikiMapper);
        ReflectionTestUtils.setField(indexService, "wikiChunkMapper", wikiChunkMapper);
        ReflectionTestUtils.setField(indexService, "wikiChunkService", wikiChunkService);
        ReflectionTestUtils.setField(indexService, "ragEmbeddingClient", embeddingClient);
        ReflectionTestUtils.setField(indexService, "ragProperties", properties);
        ReflectionTestUtils.setField(indexService, "vectorStore", vectorStore);
    }

    private DocumentWiki markdownDoc(long id, long spaceId, int version, String content) {
        DocumentWiki doc = new DocumentWiki();
        doc.setId(id);
        doc.setSpaceId(spaceId);
        doc.setContentVersion(version);
        doc.setContent(content);
        doc.setTitle("政策文档");
        doc.setContentFormat("markdown");
        doc.setIsDelete(0);
        return doc;
    }

    @Test
    void indexDocumentBuildsChunksWithMetadata() {
        String content = "---\ntitle: \"t\"\nfileNum: \"渝府发〔2026〕8号\"\n---\n\n"
                + "# 第一章 总则\n\n渝府发〔2026〕8号 本办法适用于全市范围。";
        when(documentWikiMapper.selectById(1L)).thenReturn(markdownDoc(1L, 10L, 3, content));
        when(wikiChunkMapper.invalidateByDocId(1L)).thenReturn(0);
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));
        when(wikiChunkService.saveBatch(anyList())).thenReturn(true);

        indexService.indexDocument(1L);

        ArgumentCaptor<List<WikiChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(wikiChunkService).saveBatch(captor.capture());
        List<WikiChunk> rows = captor.getValue();
        assertEquals(1, rows.size());
        WikiChunk row = rows.get(0);
        assertEquals(10L, row.getSpaceId());
        assertEquals(3, row.getContentVersion());
        assertEquals("渝府发〔2026〕8号", row.getDocNumber());
        assertEquals("政策文档", row.getDocTitle());
        assertEquals(WikiChunk.STATUS_ACTIVE, row.getStatus());
        assertNotNull(row.getEmbedding());
        assertEquals(2, VectorCodec.decode(row.getEmbedding()).length);
        verify(vectorStore).onChunksChanged(10L);
    }

    @Test
    void indexDocumentDegradesWhenEmbeddingUnavailable() {
        when(documentWikiMapper.selectById(1L)).thenReturn(markdownDoc(1L, 10L, 1, "# 标题\n\n正文"));
        when(wikiChunkMapper.invalidateByDocId(1L)).thenReturn(0);
        when(embeddingClient.embed(anyList()))
                .thenThrow(new RagEmbeddingUnavailableException("no key"));

        // must not throw — document save stays unaffected
        indexService.indexDocument(1L);

        verify(wikiChunkService, never()).saveBatch(anyList());
    }

    @Test
    void indexDocumentSkipsNonMarkdown() {
        DocumentWiki html = markdownDoc(1L, 10L, 1, "<html>…</html>");
        html.setContentFormat("html");
        when(documentWikiMapper.selectById(1L)).thenReturn(html);
        when(wikiChunkMapper.invalidateByDocId(1L)).thenReturn(0);

        indexService.indexDocument(1L);

        verify(embeddingClient, never()).embed(anyList());
        verify(wikiChunkService, never()).saveBatch(anyList());
        verify(vectorStore).onChunksChanged(10L);
    }

    @Test
    void indexDocumentInvalidatesFirstThenInserts() {
        when(documentWikiMapper.selectById(1L)).thenReturn(markdownDoc(1L, 10L, 2, "# 标题\n\n正文"));
        when(wikiChunkMapper.invalidateByDocId(1L)).thenReturn(2);
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.5f}));
        when(wikiChunkService.saveBatch(anyList())).thenReturn(true);

        indexService.indexDocument(1L);

        // stale version chunks invalidated before new insert
        verify(wikiChunkMapper).invalidateByDocId(1L);
        verify(wikiChunkService).saveBatch(anyList());
    }

    @Test
    void reactivateDocumentRebuildsWhenVersionDrifted() {
        DocumentWiki doc = markdownDoc(1L, 10L, 5, "# 标题\n\n正文");
        when(documentWikiMapper.selectById(1L)).thenReturn(doc);
        when(wikiChunkMapper.reactivateByDocIdAndVersion(1L, 3)).thenReturn(0);
        when(wikiChunkMapper.invalidateByDocId(1L)).thenReturn(0);
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.5f}));
        when(wikiChunkService.saveBatch(anyList())).thenReturn(true);

        // event says version 3, live doc is version 5 -> rebuild
        indexService.reactivateDocument(1L, 3);

        verify(wikiChunkService).saveBatch(anyList());
    }

    @Test
    void reactivateDocumentFlipsStatusWhenVersionMatches() {
        when(documentWikiMapper.selectById(1L)).thenReturn(markdownDoc(1L, 10L, 3, "# 标题\n\n正文"));
        when(wikiChunkMapper.reactivateByDocIdAndVersion(1L, 3)).thenReturn(4);

        indexService.reactivateDocument(1L, 3);

        verify(wikiChunkMapper).reactivateByDocIdAndVersion(1L, 3);
        verify(wikiChunkService, never()).saveBatch(anyList());
        verify(vectorStore).onChunksChanged(10L);
    }

    @Test
    void invalidateDocumentTouchesStoreOnlyWhenSomethingChanged() {
        when(documentWikiMapper.selectById(1L)).thenReturn(markdownDoc(1L, 10L, 1, "# 标题"));
        when(wikiChunkMapper.invalidateByDocId(1L)).thenReturn(3);
        indexService.invalidateDocument(1L);
        verify(vectorStore).onChunksChanged(10L);

        // no chunks -> no store notification needed
        org.mockito.Mockito.clearInvocations(vectorStore);
        when(documentWikiMapper.selectById(2L)).thenReturn(null);
        when(wikiChunkMapper.invalidateByDocId(2L)).thenReturn(0);
        indexService.invalidateDocument(2L);
        verify(vectorStore, never()).onChunksChanged(anyLong());
    }

    @Test
    void moveDocumentChunksUpdatesSpace() {
        indexService.moveDocumentChunks(1L, 10L, 20L);
        verify(wikiChunkMapper).moveByDocId(1L, 20L);
        verify(vectorStore).onChunksChanged(10L);
        verify(vectorStore).onChunksChanged(20L);
    }

    @Test
    void spaceLevelOperationsDelegate() {
        indexService.invalidateSpace(10L);
        verify(wikiChunkMapper).invalidateBySpaceId(10L);

        indexService.deleteSpaceChunks(10L);
        verify(wikiChunkMapper).physicallyDeleteBySpaceId(10L);
    }

    @Test
    void rebuildAllSkipsUpToDateDocs() {
        DocumentWiki upToDate = markdownDoc(1L, 10L, 2, "# a\n\n正文一");
        DocumentWiki stale = markdownDoc(2L, 10L, 5, "# b\n\n正文二");
        when(documentWikiMapper.selectList(any(Wrapper.class))).thenReturn(List.of(upToDate, stale));
        when(embeddingClient.isConfigured()).thenReturn(true);
        // isUpToDate(doc1): active>0? yes(1L); version-matching count>0? yes
        // use selectCount answers by call order
        when(wikiChunkMapper.selectCount(any(Wrapper.class))).thenReturn(1L, 1L, 0L, 2L);
        when(wikiChunkMapper.invalidateByDocId(anyLong())).thenReturn(0);
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));
        when(wikiChunkService.saveBatch(anyList())).thenReturn(true);
        // legacy normalization update for stale doc
        when(documentWikiMapper.updateById(any(DocumentWiki.class))).thenReturn(1);

        RagRebuildReport report = indexService.rebuildAll();

        assertEquals(2, report.getTotal());
        assertEquals(1, report.getSkipped());
        assertEquals(1, report.getCreated());
        assertTrue(report.getFailed().isEmpty());
    }

    @Test
    void rebuildAllReportsUnconfiguredEmbedding() {
        when(documentWikiMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(markdownDoc(1L, 10L, 1, "# a\n\n正文")));
        when(embeddingClient.isConfigured()).thenReturn(false);

        RagRebuildReport report = indexService.rebuildAll();

        assertEquals(1, report.getTotal());
        assertEquals(0, report.getCreated());
        assertEquals(1, report.getFailed().size());
    }

    @Test
    void rebuildFailurePerDocDoesNotAbortRun() {
        DocumentWiki bad = markdownDoc(1L, 10L, 1, "# a\n\n正文");
        DocumentWiki good = markdownDoc(2L, 10L, 1, "# b\n\n正文");
        when(documentWikiMapper.selectList(any(Wrapper.class))).thenReturn(List.of(bad, good));
        when(embeddingClient.isConfigured()).thenReturn(true);
        when(wikiChunkMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(wikiChunkMapper.invalidateByDocId(anyLong())).thenReturn(0);
        when(documentWikiMapper.updateById(any(DocumentWiki.class))).thenReturn(1);
        when(documentWikiMapper.selectById(1L)).thenReturn(bad);
        when(documentWikiMapper.selectById(2L)).thenReturn(good);
        // first embed call explodes, second succeeds
        when(embeddingClient.embed(anyList()))
                .thenThrow(new RagEmbeddingUnavailableException("boom"))
                .thenReturn(List.of(new float[]{0.2f}));
        when(wikiChunkService.saveBatch(anyList())).thenReturn(true);

        RagRebuildReport report = indexService.rebuildAll();

        assertEquals(2, report.getTotal());
        assertEquals(1, report.getCreated());
        assertEquals(1, report.getFailed().size());
    }

    @Test
    void reconcileCallsOrphanInvalidation() {
        when(wikiChunkMapper.invalidateOrphans()).thenReturn(7);
        assertEquals(7, indexService.reconcileOrphans());
        verify(wikiChunkMapper).invalidateOrphans();
    }

    @Test
    void listChunksOfDocumentProjectsPreview() {
        WikiChunk chunk = new WikiChunk();
        chunk.setId(9L);
        chunk.setDocId(1L);
        chunk.setChunkIndex(0);
        chunk.setChunkHeading("第一章");
        chunk.setDocNumber("渝府发〔2026〕8号");
        chunk.setStatus(WikiChunk.STATUS_ACTIVE);
        chunk.setContentVersion(1);
        chunk.setChunkText("x".repeat(300));
        when(wikiChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(chunk));

        List<WikiChunkView> views = indexService.listChunksOfDocument(1L);

        assertEquals(1, views.size());
        assertEquals("渝府发〔2026〕8号", views.get(0).getDocNumber());
        assertTrue(views.get(0).getTextPreview().endsWith("…"));
        assertTrue(views.get(0).getTextPreview().length() <= 161);
    }

    @Test
    void restoreSpaceReactivatesMatchingVersionChunks() {
        WikiChunk chunk = new WikiChunk();
        chunk.setId(9L);
        chunk.setDocId(1L);
        chunk.setSpaceId(10L);
        chunk.setContentVersion(3);
        chunk.setStatus(WikiChunk.STATUS_INVALID);
        when(wikiChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(chunk)).thenReturn(Collections.emptyList());
        when(documentWikiMapper.selectById(1L)).thenReturn(markdownDoc(1L, 10L, 3, "# a\n\n正文"));
        when(wikiChunkMapper.updateById(any(WikiChunk.class))).thenReturn(1);

        indexService.restoreSpace(10L);

        ArgumentCaptor<WikiChunk> captor = ArgumentCaptor.forClass(WikiChunk.class);
        verify(wikiChunkMapper).updateById(captor.capture());
        assertEquals(WikiChunk.STATUS_ACTIVE, captor.getValue().getStatus());
        verify(vectorStore).onChunksChanged(10L);
    }

    @Test
    void emptyQueryTextProducesNoRows() {
        when(documentWikiMapper.selectById(1L)).thenReturn(markdownDoc(1L, 10L, 1, "   "));
        when(wikiChunkMapper.invalidateByDocId(1L)).thenReturn(0);

        indexService.indexDocument(1L);

        verify(embeddingClient, never()).embed(anyList());
        verify(wikiChunkService, never()).saveBatch(anyList());
        verify(vectorStore).onChunksChanged(10L);
    }
}

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    private LexicalIndex lexicalIndex;
    private WikiRagIndexServiceImpl indexService;

    @BeforeEach
    void setUp() {
        documentWikiMapper = mock(DocumentWikiMapper.class);
        wikiChunkMapper = mock(WikiChunkMapper.class);
        wikiChunkService = mock(WikiChunkService.class);
        embeddingClient = mock(RagEmbeddingClient.class);
        vectorStore = mock(VectorStore.class);
        lexicalIndex = mock(LexicalIndex.class);
        RagProperties properties = new RagProperties();
        indexService = new WikiRagIndexServiceImpl();
        ReflectionTestUtils.setField(indexService, "documentWikiMapper", documentWikiMapper);
        ReflectionTestUtils.setField(indexService, "wikiChunkMapper", wikiChunkMapper);
        ReflectionTestUtils.setField(indexService, "wikiChunkService", wikiChunkService);
        ReflectionTestUtils.setField(indexService, "ragEmbeddingClient", embeddingClient);
        ReflectionTestUtils.setField(indexService, "ragProperties", properties);
        ReflectionTestUtils.setField(indexService, "vectorStore", vectorStore);
        ReflectionTestUtils.setField(indexService, "lexicalIndex", lexicalIndex);
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
        // both cached indexes must follow the move — not just the vector store
        verify(vectorStore).onChunksChanged(10L);
        verify(vectorStore).onChunksChanged(20L);
        verify(lexicalIndex).onChunksChanged(10L);
        verify(lexicalIndex).onChunksChanged(20L);
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

        RagRebuildReport report = indexService.rebuildAll(false);

        assertEquals(2, report.getTotal());
        assertEquals(1, report.getSkipped());
        assertEquals(1, report.getCreated());
        assertTrue(report.getFailed().isEmpty());
    }

    @Test
    void rebuildAllForceReembedsUpToDateDocs() {
        // 换 embedding 模型后的场景：文档向量已"最新"，但 force 必须跳过 isUpToDate 全部重嵌
        DocumentWiki upToDate = markdownDoc(1L, 10L, 2, "# a\n\n正文一");
        when(documentWikiMapper.selectList(any(Wrapper.class))).thenReturn(List.of(upToDate));
        when(embeddingClient.isConfigured()).thenReturn(true);
        when(documentWikiMapper.selectById(1L)).thenReturn(upToDate);
        when(wikiChunkMapper.invalidateByDocId(anyLong())).thenReturn(0);
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));
        when(wikiChunkService.saveBatch(anyList())).thenReturn(true);

        RagRebuildReport report = indexService.rebuildAll(true);

        assertEquals(1, report.getTotal());
        assertEquals(0, report.getSkipped());
        assertEquals(1, report.getCreated());
        verify(wikiChunkMapper, never()).selectCount(any(Wrapper.class));
    }

    @Test
    void rebuildAllReportsUnconfiguredEmbedding() {
        when(documentWikiMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(markdownDoc(1L, 10L, 1, "# a\n\n正文")));
        when(embeddingClient.isConfigured()).thenReturn(false);

        RagRebuildReport report = indexService.rebuildAll(false);

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

        RagRebuildReport report = indexService.rebuildAll(false);

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

    /**
     * The chunk sequence must not depend on which path produced it. A rebuild re-reads the stored
     * row, so if it re-detected the language instead of honouring the recorded one it could pick the
     * other profile, renumber every chunk and invalidate anchors that were already bound.
     */
    @Test
    void storedLanguageKeepsTheFirstIndexAndTheRebuildInStep() {
        String content = chineseBody();
        DocumentWiki doc = markdownDoc(1L, 10L, 1, content);
        doc.setContentHash("hash");
        doc.setMetadataJson("{\"language\":\"en\"}");
        when(documentWikiMapper.selectById(1L)).thenReturn(doc);
        when(wikiChunkMapper.invalidateByDocId(anyLong())).thenReturn(0);
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));
        when(wikiChunkService.saveBatch(anyList())).thenReturn(true);

        // path 1: the asynchronous index triggered right after the import commits
        List<WikiChunk> imported = captureSavedRows(() -> indexService.indexDocument(1L));

        // path 2: the same document chunks cleared, then an admin rebuild
        org.mockito.Mockito.clearInvocations(wikiChunkService, wikiChunkMapper, documentWikiMapper);
        when(documentWikiMapper.selectList(any(Wrapper.class))).thenReturn(List.of(doc));
        when(documentWikiMapper.selectById(1L)).thenReturn(doc);
        when(embeddingClient.isConfigured()).thenReturn(true);
        when(wikiChunkMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(wikiChunkMapper.invalidateByDocId(anyLong())).thenReturn(0);
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));
        when(wikiChunkService.saveBatch(anyList())).thenReturn(true);
        List<WikiChunk> rebuilt = captureSavedRows(() -> indexService.rebuildAll(false));

        assertEquals(chunkTexts(imported), chunkTexts(rebuilt),
                "a rebuild must reproduce the chunk sequence produced at import time");
        // the recorded language, not content detection, decided the profile
        assertEquals(markdownChunkTexts(MarkdownChunker.chunk(content, ChunkerProfile.EN)), chunkTexts(imported));
        assertNotEquals(markdownChunkTexts(MarkdownChunker.chunk(content, ChunkerProfile.ZH)), chunkTexts(imported),
                "the fixture must genuinely distinguish the two profiles, otherwise this proves nothing");
    }

    @Test
    void contentWithoutARecordedLanguageIsDetectedFromTheContent() {
        String content = "# News\n\nEnglish prose that carries no Chinese characters at all.";
        when(documentWikiMapper.selectById(1L)).thenReturn(markdownDoc(1L, 10L, 1, content));
        when(wikiChunkMapper.invalidateByDocId(1L)).thenReturn(0);
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));
        when(wikiChunkService.saveBatch(anyList())).thenReturn(true);

        List<WikiChunk> rows = captureSavedRows(() -> indexService.indexDocument(1L));

        assertEquals(1, rows.size());
        assertEquals("News", rows.get(0).getChunkHeading());
    }

    private List<WikiChunk> captureSavedRows(Runnable action) {
        action.run();
        ArgumentCaptor<List<WikiChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(wikiChunkService, org.mockito.Mockito.atLeastOnce()).saveBatch(captor.capture());
        List<WikiChunk> rows = new java.util.ArrayList<>();
        captor.getAllValues().forEach(rows::addAll);
        return rows;
    }

    private static List<String> chunkTexts(List<WikiChunk> rows) {
        List<String> texts = new java.util.ArrayList<>(rows.size());
        for (WikiChunk row : rows) {
            texts.add(row.getChunkIndex() + "|" + row.getChunkText());
        }
        return texts;
    }

    /**
     * Same projection as {@link #chunkTexts}, so the two can be compared directly.
     */
    private static List<String> markdownChunkTexts(List<MarkdownChunk> chunks) {
        List<String> texts = new java.util.ArrayList<>(chunks.size());
        for (MarkdownChunk chunk : chunks) {
            texts.add(chunk.getIndex() + "|" + chunk.getText());
        }
        return texts;
    }

    /** Well over the Chinese cap but under the English one, and predominantly Chinese by letters. */
    private static String chineseBody() {
        String sentence = "本办法所称困难群众救助补助资金，是指中央和市级财政安排的用于保障困难群众基本生活的专项资金，"
                + "应当专款专用并及时拨付到位。";
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            body.append("第").append(i + 1).append("条 ");
            for (int j = 0; j < 4; j++) {
                body.append(sentence);
            }
            body.append("\n\n");
        }
        return body.toString();
    }
}

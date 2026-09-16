package com.et.cloud.rag;

import com.et.cloud.mapper.WikiChunkMapper;
import com.et.cloud.model.entity.WikiChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The lexical channel exists because the dense pool alone caps recall below the
 * target; these tests pin the parts that make BM25 on Chinese text work without
 * an external segmenter.
 */
class LexicalIndexTest {

    private static final long SPACE_ID = 1L;

    private WikiChunkMapper wikiChunkMapper;
    private LexicalIndex lexicalIndex;

    @BeforeEach
    void setUp() {
        wikiChunkMapper = mock(WikiChunkMapper.class);
        lexicalIndex = new LexicalIndex();
        ReflectionTestUtils.setField(lexicalIndex, "wikiChunkMapper", wikiChunkMapper);
        ReflectionTestUtils.setField(lexicalIndex, "ragProperties", new RagProperties());
    }

    // ------------------------------------------------------------- tokenisation

    @Test
    void cjkRunsBecomeAdjacentBigrams() {
        assertEquals(List.of("低保", "保申", "申请"), LexicalIndex.tokenize("低保申请"));
    }

    @Test
    void aLoneCjkCharacterStaysAsIs() {
        assertEquals(List.of("低", "低保"), LexicalIndex.tokenize("低，低保"));
    }

    @Test
    void asciiRunsStayWholeWordsAndAreLowercased() {
        assertEquals(List.of("low", "income", "2024"), LexicalIndex.tokenize("Low income 2024"));
    }

    @Test
    void mixedTextSplitsAtTheScriptBoundary() {
        // 中英之间不加分隔符时也必须各自按自己的粒度切：中文走 bigram、英文走整词
        assertEquals(List.of("低保", "alow"), LexicalIndex.tokenize("低保aLow"));
    }

    @Test
    void punctuationAndWhitespaceAreDropped() {
        assertEquals(List.of(), LexicalIndex.tokenize("，。！？ —— \n\t"));
        assertEquals(List.of(), LexicalIndex.tokenize(""));
        assertEquals(List.of(), LexicalIndex.tokenize(null));
    }

    // ------------------------------------------------------------------ search

    @Test
    void searchReturnsEmptyOnBlankQueryOrEmptyScope() {
        assertTrue(lexicalIndex.search("", Set.of(SPACE_ID), 10).isEmpty());
        assertTrue(lexicalIndex.search("   ", Set.of(SPACE_ID), 10).isEmpty());
        assertTrue(lexicalIndex.search("低保", Set.of(), 10).isEmpty());
        assertTrue(lexicalIndex.search("低保", Set.of(SPACE_ID), 0).isEmpty());
        verify(wikiChunkMapper, times(0)).selectList(any());
    }

    @Test
    void shorterChunksWinWhenTheyMatchTheSameTerms() {
        // BM25 length normalisation: when two chunks match exactly the same query
        // terms, the one that spends less of its length on other words is the
        // better match — this is what stops a long chunk from out-ranking a
        // precise short one just by being long.
        when(wikiChunkMapper.selectList(any())).thenReturn(List.of(
                chunk(100L, "低保申请"),
                chunk(101L, "低保申请条件如下所述"),
                chunk(102L, "单位职责与办公地址")));

        List<ChunkHit> hits = lexicalIndex.search("低保申请", Set.of(SPACE_ID), 10);

        assertEquals(100L, hits.get(0).getChunkId());
        assertEquals(101L, hits.get(1).getChunkId());
        assertEquals(2, hits.size(), "a chunk sharing no term with the query is not a hit");
    }

    @Test
    void topKCapsTheLexicalResult() {
        List<WikiChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            chunks.add(chunk(200L + i, "低保申请条件第" + i + "款"));
        }
        when(wikiChunkMapper.selectList(any())).thenReturn(chunks);

        assertEquals(3, lexicalIndex.search("低保申请条件", Set.of(SPACE_ID), 3).size());
    }

    @Test
    void hitsCarryTheMetadataTheRerankerAndTheUiNeed() {
        WikiChunk chunk = chunk(100L, "低保申请条件");
        chunk.setChunkHeading("第二章 申请条件");
        chunk.setDocTitle("重庆市最低生活保障办法");
        chunk.setDocNumber("渝府办发〔2026〕24号");
        chunk.setChunkIndex(7);
        when(wikiChunkMapper.selectList(any())).thenReturn(List.of(chunk));

        ChunkHit hit = lexicalIndex.search("低保申请条件", Set.of(SPACE_ID), 10).get(0);

        assertEquals("第二章 申请条件", hit.getChunkHeading());
        assertEquals("重庆市最低生活保障办法", hit.getDocTitle());
        assertEquals("渝府办发〔2026〕24号", hit.getDocNumber());
        assertEquals(7, hit.getChunkIndex());
        assertEquals(SPACE_ID, hit.getSpaceId());
        assertTrue(hit.getScore() > 0);
    }

    // ------------------------------------------------------------------ caching

    @Test
    void theSpaceIndexIsBuiltOnceAndReusedAcrossQueries() {
        when(wikiChunkMapper.selectList(any())).thenReturn(List.of(chunk(100L, "低保申请条件")));

        lexicalIndex.search("低保", Set.of(SPACE_ID), 10);
        lexicalIndex.search("申请", Set.of(SPACE_ID), 10);

        verify(wikiChunkMapper, times(1)).selectList(any());
    }

    @Test
    void onChunksChangedForcesAReload() {
        when(wikiChunkMapper.selectList(any())).thenReturn(List.of(chunk(100L, "低保申请条件")));

        lexicalIndex.search("低保", Set.of(SPACE_ID), 10);
        lexicalIndex.onChunksChanged(SPACE_ID);
        lexicalIndex.search("低保", Set.of(SPACE_ID), 10);

        verify(wikiChunkMapper, times(2)).selectList(any());
    }

    @Test
    void eachSpaceKeepsItsOwnIndex() {
        when(wikiChunkMapper.selectList(any())).thenReturn(List.of(chunk(100L, "低保申请条件")));

        lexicalIndex.search("低保", Set.of(1L), 10);
        lexicalIndex.search("低保", Set.of(2L), 10);

        verify(wikiChunkMapper, times(2)).selectList(any());
    }

    @Test
    void anEmptySpaceSnapshotIsCachedToo() {
        when(wikiChunkMapper.selectList(any())).thenReturn(new ArrayList<>());

        assertTrue(lexicalIndex.search("低保", Set.of(3L), 10).isEmpty());
        // an empty snapshot is still cached, so the second query does not re-query
        assertTrue(lexicalIndex.search("低保", Set.of(3L), 10).isEmpty());
        verify(wikiChunkMapper, times(1)).selectList(any());
    }

    private static WikiChunk chunk(long id, String text) {
        WikiChunk chunk = new WikiChunk();
        chunk.setId(id);
        chunk.setDocId(10L);
        chunk.setSpaceId(SPACE_ID);
        chunk.setChunkIndex(0);
        chunk.setChunkText(text);
        chunk.setDocTitle("标题");
        chunk.setStatus(WikiChunk.STATUS_ACTIVE);
        return chunk;
    }
}

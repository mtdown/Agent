package com.et.cloud.rag;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.et.cloud.mapper.WikiChunkMapper;
import com.et.cloud.model.entity.WikiChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryCosineVectorStoreTest {

    private WikiChunkMapper wikiChunkMapper;
    private InMemoryCosineVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        wikiChunkMapper = mock(WikiChunkMapper.class);
        vectorStore = new InMemoryCosineVectorStore();
        ReflectionTestUtils.setField(vectorStore, "wikiChunkMapper", wikiChunkMapper);
    }

    private WikiChunk chunk(long id, long docId, long spaceId, float[] vector, String number) {
        WikiChunk chunk = new WikiChunk();
        chunk.setId(id);
        chunk.setDocId(docId);
        chunk.setSpaceId(spaceId);
        chunk.setChunkIndex(0);
        chunk.setChunkHeading("第一章");
        chunk.setChunkText("内容");
        chunk.setDocTitle("文档");
        chunk.setDocNumber(number);
        chunk.setStatus(WikiChunk.STATUS_ACTIVE);
        chunk.setIsDelete(0);
        chunk.setEmbedding(VectorCodec.encode(vector));
        return chunk;
    }

    @Test
    void searchRanksByCosineWithinSpaceFilter() {
        when(wikiChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                chunk(1L, 11L, 10L, new float[]{1f, 0f}, "渝府发〔2026〕1号"),
                chunk(2L, 12L, 10L, new float[]{0f, 1f}, null),
                chunk(3L, 13L, 20L, new float[]{1f, 0f}, null)));

        List<ChunkHit> hits = vectorStore.search(new float[]{1f, 0f}, Set.of(10L), 3);

        assertEquals(2, hits.size(), "space 20 excluded by filter");
        assertEquals(1L, hits.get(0).getChunkId(), "aligned vector ranks first");
        assertEquals(11L, hits.get(0).getDocId());
        assertEquals("渝府发〔2026〕1号", hits.get(0).getDocNumber());
        assertTrue(hits.get(0).getScore() > hits.get(1).getScore());
    }

    @Test
    void topKTruncates() {
        when(wikiChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                chunk(1L, 11L, 10L, new float[]{1f}, null),
                chunk(2L, 12L, 10L, new float[]{0.9f}, null),
                chunk(3L, 13L, 10L, new float[]{0.8f}, null)));

        assertEquals(2, vectorStore.search(new float[]{1f}, Set.of(10L), 2).size());
    }

    @Test
    void chunkChangeNotificationReloads() {
        when(wikiChunkMapper.selectList(any(Wrapper.class))).thenReturn(
                List.of(chunk(1L, 11L, 10L, new float[]{1f}, null)),
                List.of(chunk(1L, 11L, 10L, new float[]{1f}, null),
                        chunk(2L, 12L, 10L, new float[]{0.9f}, null)));

        assertEquals(1, vectorStore.search(new float[]{1f}, Set.of(10L), 5).size());
        vectorStore.onChunksChanged(10L);
        assertEquals(2, vectorStore.search(new float[]{1f}, Set.of(10L), 5).size());
    }

    @Test
    void nullOrEmptyInputsReturnEmpty() {
        assertTrue(vectorStore.search(null, Set.of(1L), 5).isEmpty());
        assertTrue(vectorStore.search(new float[]{1f}, Set.of(), 5).isEmpty());
        assertTrue(vectorStore.search(new float[]{1f}, null, 5).isEmpty());
        assertTrue(vectorStore.search(new float[]{1f}, Set.of(1L), 0).isEmpty());
    }

    @Test
    void vectorCodecRoundTrip() {
        float[] vector = {0.1f, -0.5f, 3.2f};
        float[] decoded = VectorCodec.decode(VectorCodec.encode(vector));
        assertEquals(3, decoded.length);
        for (int i = 0; i < vector.length; i++) {
            assertEquals(vector[i], decoded[i], 1e-7f);
        }
        assertEquals(null, VectorCodec.decode(null));
        assertEquals(null, VectorCodec.decode(new byte[]{1, 2, 3}));
    }
}

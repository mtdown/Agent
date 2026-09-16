package com.et.cloud.rag;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RRF is what lets the dense channel and the BM25 channel be combined at all:
 * cosine similarity and BM25 are not on a common scale, so fusing by rank is a
 * deliberate choice rather than a shortcut.
 */
class RrfFusionTest {

    private static ChunkHit hit(long id, String text) {
        return new ChunkHit(id, 1L, 1L, 0, null, text, "标题", null, 0.5d);
    }

    private static List<Long> ids(List<ChunkHit> hits) {
        return hits.stream().map(ChunkHit::getChunkId).collect(java.util.stream.Collectors.toList());
    }

    @Test
    void chunksFoundByBothChannelsOutrankChunksFoundByOne() {
        // dense: A, B   lexical: B, C   ->  B is corroborated, so it must lead
        List<ChunkHit> fused = RrfFusion.fuse(
                List.of(List.of(hit(1L, "A"), hit(2L, "B")), List.of(hit(2L, "B"), hit(3L, "C"))), 60, 0);
        assertEquals(List.of(2L, 1L, 3L), ids(fused));
    }

    @Test
    void duplicateChunkIdsAreMergedNotRepeated() {
        List<ChunkHit> fused = RrfFusion.fuse(
                List.of(List.of(hit(1L, "A"), hit(2L, "B")), List.of(hit(2L, "B"), hit(1L, "A"))), 60, 0);
        assertEquals(2, fused.size());
    }

    @Test
    void theFusedHitKeepsTheMetadataOfItsFirstAppearance() {
        List<ChunkHit> fused = RrfFusion.fuse(
                List.of(List.of(hit(1L, "来自稠密通道")), List.of(hit(1L, "来自词法通道"))), 60, 0);
        assertEquals("来自稠密通道", fused.get(0).getChunkText());
    }

    @Test
    void theFusedScoreIsTheSummedReciprocalRank() {
        List<ChunkHit> fused = RrfFusion.fuse(
                List.of(List.of(hit(1L, "A")), List.of(hit(1L, "A"))), 60, 0);
        assertEquals(2.0 / 61.0, fused.get(0).getScore(), 1e-12);
    }

    @Test
    void aSmallerConstantSharpensTheRankWeighting() {
        // k is a real knob, not decoration: within one channel, lowering it makes
        // the gap between rank 1 and rank 2 much steeper
        double gentleGap = rankGap(60);
        double sharpGap = rankGap(1);
        assertTrue(sharpGap > gentleGap, "sharp=" + sharpGap + " gentle=" + gentleGap);
    }

    private static double rankGap(int k) {
        List<ChunkHit> fused = RrfFusion.fuse(List.of(List.of(hit(1L, "A"), hit(2L, "B"))), k, 0);
        return fused.get(0).getScore() - fused.get(1).getScore();
    }

    @Test
    void topNLimitsThePoolAndIsZeroSafe() {
        List<ChunkHit> limited = RrfFusion.fuse(
                List.of(List.of(hit(1L, "A"), hit(2L, "B"), hit(3L, "C"))), 60, 2);
        assertEquals(2, limited.size());
        List<ChunkHit> unlimited = RrfFusion.fuse(
                List.of(List.of(hit(1L, "A"), hit(2L, "B"), hit(3L, "C"))), 60, 0);
        assertEquals(3, unlimited.size());
    }

    @Test
    void emptyAndNullChannelListsAreIgnored() {
        List<ChunkHit> fused = RrfFusion.fuse(
                List.of(Collections.emptyList(), List.of(hit(1L, "A"))), 60, 0);
        assertEquals(List.of(1L), ids(fused));
        assertTrue(RrfFusion.fuse(Collections.emptyList(), 60, 0).isEmpty());
        assertTrue(RrfFusion.fuse(List.of(Collections.emptyList()), 60, 0).isEmpty());
    }

    @Test
    void aChannelDisabledByNullStillLetsTheOtherChannelThrough() {
        // the lexical channel returns an empty list when disabled, but a null list
        // must not blow up the pipeline either (List.of forbids nulls, hence asList)
        List<ChunkHit> fused = RrfFusion.fuse(
                java.util.Arrays.asList(List.of(hit(1L, "A")), null), 60, 0);
        assertEquals(List.of(1L), ids(fused));
    }

    @Test
    void dedupeKeepsTheFirstOccurrenceOrderStably() {
        List<ChunkHit> deduped = RrfFusion.dedupe(
                List.of(hit(2L, "B"), hit(1L, "A"), hit(2L, "B"), hit(3L, "C")));
        assertEquals(List.of(2L, 1L, 3L), ids(deduped));
    }
}

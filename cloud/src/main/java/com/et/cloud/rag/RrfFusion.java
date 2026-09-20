package com.et.cloud.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion over several ranked hit lists.
 *
 * <p>RRF combines channels by rank rather than by score, which matters here
 * because cosine similarity and BM25 live on incomparable scales — normalising
 * them would require a calibration the corpus cannot justify. The constant is
 * kept at the literature default (60) on purpose: it is NOT tuned against the
 * eval set, so the reported gain stays honest.
 */
public final class RrfFusion {

    private RrfFusion() {
    }

    /**
     * Fuses ranked hit lists into one de-duplicated list ordered by summed
     * reciprocal rank. The first list encountered for a chunk supplies the
     * carried metadata; ties keep the order of first appearance.
     *
     * @param rankedLists ordered hit lists (best first); null/empty lists are ignored
     * @param k           RRF constant (typically 60)
     * @param topN        maximum number of fused hits to return (<=0 means unlimited)
     */
    public static List<ChunkHit> fuse(List<List<ChunkHit>> rankedLists, int k, int topN) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        Map<Long, ChunkHit> firstSeen = new HashMap<>();
        Map<Long, Integer> bestRank = new HashMap<>();
        for (List<ChunkHit> list : rankedLists) {
            if (list == null || list.isEmpty()) {
                continue;
            }
            int rank = 1;
            for (ChunkHit hit : list) {
                if (hit == null) {
                    continue;
                }
                Long id = hit.getChunkId();
                scores.merge(id, 1.0 / (k + rank), Double::sum);
                firstSeen.putIfAbsent(id, hit);
                bestRank.merge(id, rank, Math::min);
                rank++;
            }
        }
        List<Map.Entry<Long, Double>> ordered = new ArrayList<>(scores.entrySet());
        ordered.sort(Comparator.<Map.Entry<Long, Double>, Double>comparing(Map.Entry::getValue).reversed()
                .thenComparing(e -> bestRank.getOrDefault(e.getKey(), Integer.MAX_VALUE)));

        List<ChunkHit> out = new ArrayList<>(ordered.size());
        for (Map.Entry<Long, Double> e : ordered) {
            ChunkHit hit = firstSeen.get(e.getKey());
            ChunkHit fused = new ChunkHit(hit.getChunkId(), hit.getDocId(), hit.getSpaceId(), hit.getChunkIndex(),
                    hit.getChunkHeading(), hit.getChunkText(), hit.getDocTitle(), hit.getDocNumber(), e.getValue());
            fused.setOriginalChunkIds(new ArrayList<>(hit.getOriginalChunkIds()));
            fused.setOriginalChunkIndexes(new ArrayList<>(hit.getOriginalChunkIndexes()));
            fused.setOriginalChunkScores(new ArrayList<>(hit.getOriginalChunkScores()));
            out.add(fused);
            if (topN > 0 && out.size() >= topN) {
                break;
            }
        }
        return out;
    }

    /** Convenience overload used by the search pipeline. */
    public static List<ChunkHit> fuse(List<List<ChunkHit>> rankedLists, int topN) {
        return fuse(rankedLists, 60, topN);
    }

    /** Stable de-duplication by chunk id, keeping the first (best-ranked) occurrence. */
    public static List<ChunkHit> dedupe(List<ChunkHit> hits) {
        Map<Long, ChunkHit> seen = new LinkedHashMap<>();
        for (ChunkHit hit : hits) {
            if (hit != null) {
                seen.putIfAbsent(hit.getChunkId(), hit);
            }
        }
        return new ArrayList<>(seen.values());
    }
}

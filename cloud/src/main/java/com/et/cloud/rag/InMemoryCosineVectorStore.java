package com.et.cloud.rag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.et.cloud.mapper.WikiChunkMapper;
import com.et.cloud.model.entity.WikiChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cosine-similarity vector store. Loads ACTIVE chunk vectors per
 * space lazily and reloads a space on change notifications. Sized for the
 * demo corpus (thousands of chunks); swap the bean for ES when exceeding that.
 */
@Component
@Slf4j
public class InMemoryCosineVectorStore implements VectorStore {

    @Resource
    private WikiChunkMapper wikiChunkMapper;

    /**
     * spaceId -> immutable snapshot of cached entries (copy-on-write replace).
     */
    private final Map<Long, List<CachedVector>> cache = new ConcurrentHashMap<>();

    private static final int MAX_TEXT_PREVIEW = 2000;

    @Override
    public List<ChunkHit> search(float[] queryVector, Set<Long> spaceIds, int topK) {
        List<ChunkHit> hits = new ArrayList<>();
        if (queryVector == null || queryVector.length == 0 || spaceIds == null || spaceIds.isEmpty() || topK <= 0) {
            return hits;
        }
        for (Long spaceId : spaceIds) {
            List<CachedVector> entries = cache.computeIfAbsent(spaceId, this::loadSpace);
            for (CachedVector entry : entries) {
                // defensive: skip stale rows that slipped into a wrong space snapshot
                if (!spaceId.equals(entry.spaceId)) {
                    continue;
                }
                double score = cosine(queryVector, entry.vector);
                hits.add(new ChunkHit(entry.chunkId, entry.docId, entry.spaceId, entry.chunkIndex,
                        entry.chunkHeading, entry.chunkText, entry.docTitle, entry.docNumber, score));
            }
        }
        hits.sort(Comparator.comparingDouble(ChunkHit::getScore).reversed());
        return hits.size() > topK ? new ArrayList<>(hits.subList(0, topK)) : hits;
    }

    @Override
    public void onChunksChanged(Long spaceId) {
        if (spaceId == null) {
            return;
        }
        // drop the snapshot; next search reloads from DB
        cache.remove(spaceId);
    }

    private List<CachedVector> loadSpace(Long spaceId) {
        QueryWrapper<WikiChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("spaceId", spaceId).eq("status", WikiChunk.STATUS_ACTIVE).eq("isDelete", 0);
        List<WikiChunk> chunks = wikiChunkMapper.selectList(wrapper);
        List<CachedVector> entries = new ArrayList<>(chunks.size());
        for (WikiChunk chunk : chunks) {
            float[] vector = VectorCodec.decode(chunk.getEmbedding());
            if (vector == null || vector.length == 0) {
                continue;
            }
            String text = chunk.getChunkText();
            if (text != null && text.length() > MAX_TEXT_PREVIEW) {
                text = text.substring(0, MAX_TEXT_PREVIEW);
            }
            entries.add(new CachedVector(chunk.getId(), chunk.getDocId(), chunk.getSpaceId(),
                    chunk.getChunkIndex(), chunk.getChunkHeading(), text, chunk.getDocTitle(),
                    chunk.getDocNumber(), vector));
        }
        log.debug("vector store loaded space {}: {} chunks", spaceId, entries.size());
        return entries;
    }

    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            return -1;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static final class CachedVector {
        private final Long chunkId;
        private final Long docId;
        private final Long spaceId;
        private final Integer chunkIndex;
        private final String chunkHeading;
        private final String chunkText;
        private final String docTitle;
        private final String docNumber;
        private final float[] vector;

        private CachedVector(Long chunkId, Long docId, Long spaceId, Integer chunkIndex, String chunkHeading,
                             String chunkText, String docTitle, String docNumber, float[] vector) {
            this.chunkId = chunkId;
            this.docId = docId;
            this.spaceId = spaceId;
            this.chunkIndex = chunkIndex;
            this.chunkHeading = chunkHeading;
            this.chunkText = chunkText;
            this.docTitle = docTitle;
            this.docNumber = docNumber;
            this.vector = vector;
        }
    }
}

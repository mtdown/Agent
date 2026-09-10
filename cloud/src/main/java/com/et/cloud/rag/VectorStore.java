package com.et.cloud.rag;

import java.util.List;
import java.util.Set;

/**
 * Vector retrieval abstraction. The default implementation is an in-memory
 * cosine store backed by the wiki_chunk table; an Elasticsearch/BM25+KNN
 * implementation can replace the bean without touching business code.
 */
public interface VectorStore {

    /**
     * Returns the top-K most similar ACTIVE chunks within the given spaces.
     * Space filtering happens BEFORE similarity ranking — callers pass the
     * already permission-filtered effective space set.
     */
    List<ChunkHit> search(float[] queryVector, Set<Long> spaceIds, int topK);

    /**
     * Notifies the store that chunks of the given space changed (indexing,
     * invalidation, restore, delete) so it can reload its cache.
     */
    void onChunksChanged(Long spaceId);
}

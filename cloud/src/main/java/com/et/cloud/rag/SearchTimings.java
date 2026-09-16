package com.et.cloud.rag;

import lombok.Data;

/**
 * Per-phase retrieval timings in milliseconds, filled by the search pipeline
 * so the ask flow (and /rag/search callers) can expose a step timeline.
 * A phase that did not run — e.g. vector search skipped when doc-number
 * exact hits saturate topK — keeps a null value instead of a misleading 0.
 */
@Data
public class SearchTimings {

    /** Resolving user-visible spaces intersected with the requested scope. */
    private Long permissionMs;

    /** Counting distinct authorized documents in the effective scope. */
    private Long docCountMs;

    /** Doc-number exact-match layer (0-ish when the query carries no doc number). */
    private Long docNumberMs;

    /** Embedding the query text via the configured embedding endpoint. */
    private Long embedMs;

    /** In-memory cosine search over cached chunk vectors. */
    private Long vectorMs;

    /** BM25 lexical channel over the same authorized corpus. */
    private Long lexicalMs;

    /** Reciprocal-rank fusion of the channels into one candidate pool. */
    private Long fusionMs;

    /** Cross-encoder re-ranking of the candidate pool (null when disabled or failed). */
    private Long rerankMs;

    /** Whole search() call. */
    private Long totalMs;
}

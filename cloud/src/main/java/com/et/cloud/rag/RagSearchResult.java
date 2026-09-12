package com.et.cloud.rag;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Retrieval result: ranked hits plus the effective (permission-filtered)
 * scope, so callers can display "检索范围：X 个空间 / N 篇授权文档".
 */
@Data
public class RagSearchResult {

    private List<ChunkHit> hits = new ArrayList<>();

    /**
     * Spaces actually searched = user-visible ∩ requested.
     */
    private LinkedHashSet<Long> effectiveSpaceIds = new LinkedHashSet<>();

    /**
     * Distinct authorized documents indexed in the effective spaces.
     */
    private long authorizedDocCount;

    /**
     * Per-phase timings of this search call (never null; unexecuted phases stay null).
     */
    private SearchTimings timings = new SearchTimings();
}

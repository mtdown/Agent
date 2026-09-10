package com.et.cloud.rag;

import lombok.Data;

import java.util.List;

/**
 * Retrieval request. spaceIds == null means "all spaces visible to the user".
 */
@Data
public class RagSearchRequest {

    /**
     * Natural-language query.
     */
    private String query;

    /**
     * Requested space scope (nullable = all visible spaces). Spaces the user
     * cannot see are filtered out by the service, never by the caller.
     */
    private List<Long> spaceIds;

    /**
     * Top-K, falls back to the configured default.
     */
    private Integer topK;
}

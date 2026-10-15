package com.et.cloud.rag;

import lombok.Data;

import java.util.List;

/**
 * Retrieval request. Omitted space and folder scopes mean all visible spaces.
 */
@Data
public class RagSearchRequest {

    /**
     * Natural-language query.
     */
    private String query;

    /**
     * Requested whole-space scope (nullable without folders = all visible spaces). Spaces the user
     * cannot see are filtered out by the service, never by the caller.
     */
    private List<Long> spaceIds;

    /** Selected WIKI folders; each includes its descendant folders. */
    private List<Long> folderIds;

    /**
     * Top-K, falls back to the configured default.
     */
    private Integer topK;
}

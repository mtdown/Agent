package com.et.cloud.rag;

import com.et.cloud.model.entity.User;

/**
 * Permission-filtered semantic retrieval. This is the single entry point
 * reused by the AI assistant panel (change 2) and the open API (change 3).
 */
public interface RagSearchService {

    /**
     * Searches authorized chunks. Permission filtering happens here —
     * BEFORE similarity ranking — based on spaces visible to the user.
     */
    RagSearchResult search(User loginUser, RagSearchRequest request);
}

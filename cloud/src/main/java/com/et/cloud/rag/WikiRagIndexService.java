package com.et.cloud.rag;

import java.util.List;

/**
 * RAG index operations. All methods are safe to call asynchronously and MUST
 * never throw upward into document persistence flows.
 */
public interface WikiRagIndexService {

    /**
     * (Re)builds the chunk index of one document: invalidates existing ACTIVE
     * chunks, slices, embeds and inserts new chunks.
     */
    void indexDocument(Long docId);

    void invalidateDocument(Long docId);

    /**
     * Reactivates chunks of the given version; falls back to a full rebuild
     * when the version changed (or no chunks exist).
     */
    void reactivateDocument(Long docId, Integer contentVersion);

    void deleteDocumentChunks(Long docId, Long spaceId);

    void moveDocumentChunks(Long docId, Long fromSpaceId, Long toSpaceId);

    void invalidateSpace(Long spaceId);

    /**
     * Space restored: reactivate chunks whose doc is back alive, drop orphans.
     */
    void restoreSpace(Long spaceId);

    void deleteSpaceChunks(Long spaceId);

    /**
     * Admin backfill: (re)builds indexes for all live Markdown documents.
     * Idempotent — documents whose ACTIVE chunks already match their content
     * version are skipped, unless force is set (used after switching the
     * embedding model: all vectors must be regenerated with the new model).
     */
    RagRebuildReport rebuildAll(boolean force);

    /**
     * Daily reconciliation: invalidate ACTIVE chunks whose document is gone
     * or logically deleted. Returns how many orphans were fixed.
     */
    int reconcileOrphans();

    /**
     * Lists chunks of a document (for the detail-page chunk preview).
     */
    List<WikiChunkView> listChunksOfDocument(Long docId);
}

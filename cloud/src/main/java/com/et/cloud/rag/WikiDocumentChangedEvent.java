package com.et.cloud.rag;

import lombok.Getter;

/**
 * Document lifecycle change published by DocumentWikiService (and space-level
 * batch operations). Consumed AFTER_COMMIT by the RAG index listener.
 */
@Getter
public class WikiDocumentChangedEvent {

    public enum ChangeType {
        DOC_CREATED,
        DOC_UPDATED,
        DOC_LOGICAL_DELETED,
        DOC_RESTORED,
        DOC_PERMANENT_DELETED,
        /** doc moved between spaces (folder move stays inside the same space) */
        DOC_MOVED,
        SPACE_LOGICAL_DELETED,
        SPACE_RESTORED,
        SPACE_PERMANENT_DELETED
    }

    private final ChangeType changeType;

    private final Long docId;

    private final Long spaceId;

    /** only for DOC_MOVED: the destination space */
    private final Long targetSpaceId;

    /** content version after the change (doc-level events only) */
    private final Integer contentVersion;

    public WikiDocumentChangedEvent(ChangeType changeType, Long docId, Long spaceId,
                                    Long targetSpaceId, Integer contentVersion) {
        this.changeType = changeType;
        this.docId = docId;
        this.spaceId = spaceId;
        this.targetSpaceId = targetSpaceId;
        this.contentVersion = contentVersion;
    }

    public static WikiDocumentChangedEvent of(ChangeType type, Long docId, Long spaceId, Integer contentVersion) {
        return new WikiDocumentChangedEvent(type, docId, spaceId, null, contentVersion);
    }

    public static WikiDocumentChangedEvent moved(Long docId, Long fromSpaceId, Long toSpaceId) {
        return new WikiDocumentChangedEvent(ChangeType.DOC_MOVED, docId, fromSpaceId, toSpaceId, null);
    }

    public static WikiDocumentChangedEvent spaceEvent(ChangeType type, Long spaceId) {
        return new WikiDocumentChangedEvent(type, null, spaceId, null, null);
    }
}

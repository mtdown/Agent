package com.et.cloud.dto.documentWiki;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DocumentWikiEditRequest implements Serializable {

    /**
     * Document id.
     */
    private Long id;

    /**
     * Document title.
     */
    private String title;

    /**
     * Saved document body.
     */
    private String content;

    /**
     * Short summary.
     */
    private String summary;

    /**
     * Tags.
     */
    private List<String> tags;

    /**
     * Current wiki space. Kept for client compatibility; edit does not move.
     */
    private Long spaceId;

    /**
     * Current folder. Kept for client compatibility; edit does not move.
     */
    private Long folderId;

    /**
     * Content format: plain / markdown. Null keeps the stored value.
     */
    private String contentFormat;

    /**
     * Source type: NATIVE / UPLOAD / IMPORT / URL.
     */
    private String sourceType;

    /**
     * Original source url for tracing.
     */
    private String sourceUrl;

    /**
     * Content fingerprint. Reserved for later RAG workflows.
     */
    private String contentHash;

    /**
     * Content version. Reserved for later RAG workflows.
     */
    private Integer contentVersion;

    /**
     * Visibility marker. Reserved for later RAG workflows.
     */
    private String visibility;

    /**
     * Extensible metadata JSON.
     */
    private String metadataJson;

    private static final long serialVersionUID = 1L;
}

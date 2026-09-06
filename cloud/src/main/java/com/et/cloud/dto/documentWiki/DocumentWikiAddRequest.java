package com.et.cloud.dto.documentWiki;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DocumentWikiAddRequest implements Serializable {

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
     * Destination wiki space.
     */
    private Long spaceId;

    /**
     * Destination folder. Null means the space root.
     */
    private Long folderId;

    /**
     * Content format: plain / markdown. Defaults to markdown server-side.
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

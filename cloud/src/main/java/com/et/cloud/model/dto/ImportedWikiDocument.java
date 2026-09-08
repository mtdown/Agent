package com.et.cloud.model.dto;

import lombok.Data;

@Data
public class ImportedWikiDocument {

    private String title;

    private String content;

    private String contentFormat;

    private String sourceType;

    /**
     * Original url for webpage imports, blank for local uploads.
     */
    private String sourceUrl;

    private String metadataJson;
}

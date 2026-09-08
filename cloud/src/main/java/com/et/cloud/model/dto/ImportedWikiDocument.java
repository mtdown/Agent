package com.et.cloud.model.dto;

import lombok.Data;

@Data
public class ImportedWikiDocument {

    private String title;

    private String content;

    private String contentFormat;

    private String sourceType;

    private String metadataJson;
}

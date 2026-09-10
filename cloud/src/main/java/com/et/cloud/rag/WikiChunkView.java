package com.et.cloud.rag;

import lombok.Data;

/**
 * Chunk row projection for the document detail-page preview.
 */
@Data
public class WikiChunkView {

    private Long id;

    private Long docId;

    private Integer chunkIndex;

    private String chunkHeading;

    private String docNumber;

    private String status;

    private Integer contentVersion;

    private String textPreview;

    public WikiChunkView() {
    }

    public WikiChunkView(Long id, Long docId, Integer chunkIndex, String chunkHeading,
                         String docNumber, String status, Integer contentVersion, String textPreview) {
        this.id = id;
        this.docId = docId;
        this.chunkIndex = chunkIndex;
        this.chunkHeading = chunkHeading;
        this.docNumber = docNumber;
        this.status = status;
        this.contentVersion = contentVersion;
        this.textPreview = textPreview;
    }
}

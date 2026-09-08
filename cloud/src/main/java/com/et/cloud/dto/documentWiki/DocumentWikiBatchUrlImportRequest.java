package com.et.cloud.dto.documentWiki;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Batch webpage import request: a list of urls plus the Wiki destination.
 */
@Data
public class DocumentWikiBatchUrlImportRequest implements Serializable {

    private Long spaceId;

    private Long folderId;

    /**
     * Webpage urls. Entries may also be newline separated inside a single string.
     */
    private List<String> urls;

    private static final long serialVersionUID = 1L;
}

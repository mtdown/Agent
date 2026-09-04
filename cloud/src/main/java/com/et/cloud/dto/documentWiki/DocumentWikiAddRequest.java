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

    private static final long serialVersionUID = 1L;
}

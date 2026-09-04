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

    private static final long serialVersionUID = 1L;
}

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
     * Category.
     */
    private String category;

    /**
     * Tags.
     */
    private List<String> tags;

    private static final long serialVersionUID = 1L;
}

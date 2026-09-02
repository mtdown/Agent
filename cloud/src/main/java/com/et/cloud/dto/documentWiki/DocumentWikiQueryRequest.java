package com.et.cloud.dto.documentWiki;

import com.et.cloud.commen.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class DocumentWikiQueryRequest extends PageRequest implements Serializable {

    /**
     * Document id.
     */
    private Long id;

    /**
     * Document title.
     */
    private String title;

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

    /**
     * Search keyword.
     */
    private String searchText;

    /**
     * Creator user id.
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}

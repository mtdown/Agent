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
     * Tags.
     */
    private List<String> tags;

    /**
     * Search keyword.
     */
    private String searchText;

    /**
     * title, titleOrContent, content.
     */
    private String matchMode;

    private Long spaceId;

    private Long folderId;

    /**
     * Creator user id.
     */
    private Long userId;

    /**
     * Space type filter (0 personal, 1 team, 2 public). Set when a navigation region node such
     * as "公开文档" is selected so the query lists documents across every visible space of that
     * type instead of a single space.
     */
    private Integer spaceType;

    /**
     * Filled by the controller after visibility checks.
     */
    private List<Long> visibleSpaceIds;

    private static final long serialVersionUID = 1L;
}

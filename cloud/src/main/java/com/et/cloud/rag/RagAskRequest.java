package com.et.cloud.rag;

import lombok.Data;

import java.util.List;

/**
 * Ask request for the assistant chat panel.
 */
@Data
public class RagAskRequest {

    private String query;

    /**
     * Optional whole-space scope; null/empty with no folders = all visible spaces.
     */
    private List<Long> spaceIds;

    /** Optional folder subtree scope, combined with selected spaces. */
    private List<Long> folderIds;

    private Integer topK;

    /**
     * true = thinking model (slower, deeper reasoning); null/false = fast model.
     */
    private Boolean deepThinking;
}

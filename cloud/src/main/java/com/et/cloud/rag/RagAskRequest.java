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
     * Optional scope; null/empty = all visible spaces.
     */
    private List<Long> spaceIds;

    private Integer topK;

    /**
     * true = thinking model (slower, deeper reasoning); null/false = fast model.
     */
    private Boolean deepThinking;
}

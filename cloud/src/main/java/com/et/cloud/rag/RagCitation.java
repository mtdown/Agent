package com.et.cloud.rag;

import lombok.Data;

/**
 * Citation metadata sent in the SSE meta event. The index matches the [n]
 * markers the LLM is instructed to emit; all fields come from retrieval hits,
 * never from LLM text.
 */
@Data
public class RagCitation {

    private int index;

    private Long docId;

    private String docTitle;

    private String docNumber;

    private Integer chunkIndex;

    private String chunkHeading;

    private String chunkText;
}

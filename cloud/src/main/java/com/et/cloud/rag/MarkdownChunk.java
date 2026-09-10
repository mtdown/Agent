package com.et.cloud.rag;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * One semantic slice produced by {@link MarkdownChunker}. Pure data, no persistence coupling.
 */
@Data
@AllArgsConstructor
public class MarkdownChunk {

    /**
     * Zero-based chunk index inside the document.
     */
    private int index;

    /**
     * Heading path, e.g. "三、补助标准 > (二)发放方式". Empty for the pre-heading lead.
     */
    private String headingPath;

    /**
     * Chunk text (may include overlap carried from the previous chunk).
     */
    private String text;
}

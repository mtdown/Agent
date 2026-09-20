package com.et.cloud.rag;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * One retrieval hit from the vector store.
 */
@Data
public class ChunkHit {

    private Long chunkId;

    private Long docId;

    private Long spaceId;

    private Integer chunkIndex;

    private String chunkHeading;

    private String chunkText;

    private String docTitle;

    private String docNumber;

    private double score;

    private List<Long> originalChunkIds = new ArrayList<>();

    private List<Integer> originalChunkIndexes = new ArrayList<>();

    private List<Double> originalChunkScores = new ArrayList<>();

    private String evidenceGroupId;

    public ChunkHit() {
    }

    public ChunkHit(Long chunkId, Long docId, Long spaceId, Integer chunkIndex, String chunkHeading,
                    String chunkText, String docTitle, String docNumber, double score) {
        this.chunkId = chunkId;
        this.docId = docId;
        this.spaceId = spaceId;
        this.chunkIndex = chunkIndex;
        this.chunkHeading = chunkHeading;
        this.chunkText = chunkText;
        this.docTitle = docTitle;
        this.docNumber = docNumber;
        this.score = score;
        this.originalChunkIds.add(chunkId);
        this.originalChunkIndexes.add(chunkIndex);
        this.originalChunkScores.add(score);
    }
}

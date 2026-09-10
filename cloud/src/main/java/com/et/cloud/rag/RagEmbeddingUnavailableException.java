package com.et.cloud.rag;

/**
 * Raised when the embedding endpoint is not configured or unreachable.
 * Indexing callers treat this as a degrade-and-skip condition, never a document failure.
 */
public class RagEmbeddingUnavailableException extends RuntimeException {

    public RagEmbeddingUnavailableException(String message) {
        super(message);
    }

    public RagEmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

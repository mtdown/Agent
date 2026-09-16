package com.et.cloud.rag;

/**
 * Raised when the re-ranking endpoint is unreachable, misconfigured or returns
 * an unusable payload. The search pipeline catches it and falls back to the
 * fused ranking — retrieval must never fail because an optional re-ranker is
 * down.
 */
public class RagRerankUnavailableException extends RuntimeException {

    public RagRerankUnavailableException(String message) {
        super(message);
    }

    public RagRerankUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

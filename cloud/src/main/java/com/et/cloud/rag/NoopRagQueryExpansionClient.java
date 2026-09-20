package com.et.cloud.rag;

import org.springframework.stereotype.Component;

/**
 * Default-safe query expansion. It deliberately returns no generated text unless
 * a production client is wired later; retrieval must always work from the
 * original question alone.
 */
@Component
public class NoopRagQueryExpansionClient implements RagQueryExpansionClient {

    @Override
    public RagQueryExpansion expand(String query) {
        return new RagQueryExpansion();
    }
}

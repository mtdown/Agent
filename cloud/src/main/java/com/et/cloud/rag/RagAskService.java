package com.et.cloud.rag;

import com.et.cloud.model.entity.User;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Streams an RAG answer over SSE: meta (scope + citations) -> reason deltas ->
 * content deltas -> done | error.
 */
public interface RagAskService {

    /**
     * Starts an async ask; returns the emitter the controller flushes to the client.
     * Validation failures (not logged in / blank query) throw before any SSE event.
     */
    SseEmitter ask(User loginUser, RagAskRequest request);
}

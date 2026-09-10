package com.et.cloud.rag;

import com.et.cloud.model.entity.User;

import java.util.List;

/**
 * Owner-scoped management and authentication for open-RAG API keys.
 * The plaintext key exists only in the create response; storage keeps a
 * SHA-256 hash so a leaked database does not expose usable keys.
 */
public interface RagApiKeyService {

    /**
     * Creates a key for the login user and returns the plaintext exactly once.
     */
    RagApiKeyCreatedView create(User loginUser, String keyName);

    /**
     * Lists the user's active keys (never includes the plaintext).
     */
    List<RagApiKeyView> list(User loginUser);

    /**
     * Soft-deletes (revokes) a key; only the owner may delete it.
     *
     * @return true when deleted, false when the id does not belong to the user
     */
    boolean delete(User loginUser, long id);

    /**
     * Resolves the owner of a presented key.
     *
     * @throws com.et.cloud.exception.BusinessException 40101 for missing / malformed /
     *         revoked keys — uniform message, no distinction between cases
     */
    User resolveUser(String apiKey);
}

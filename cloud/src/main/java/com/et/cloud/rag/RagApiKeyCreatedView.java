package com.et.cloud.rag;

import lombok.Data;

import java.util.Date;

/**
 * Create response carrying the plaintext key exactly once.
 */
@Data
public class RagApiKeyCreatedView {

    private Long id;

    private String keyName;

    private String keyPrefix;

    /**
     * Full plaintext key — returned only here, never again.
     */
    private String apiKey;

    private Date createTime;
}

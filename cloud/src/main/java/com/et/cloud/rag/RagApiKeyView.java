package com.et.cloud.rag;

import lombok.Data;

import java.util.Date;

/**
 * API key row as shown in the management list (no plaintext).
 */
@Data
public class RagApiKeyView {

    private Long id;

    private String keyName;

    /**
     * First chars of the plaintext, e.g. "cpk_a1b2c3…".
     */
    private String keyPrefix;

    private Date createTime;
}

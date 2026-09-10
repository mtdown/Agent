package com.et.cloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * User-owned API key for the open RAG API. The plaintext key is never stored;
 * only its SHA-256 hash is persisted and used for authentication lookups.
 *
 * @TableName rag_api_key
 */
@TableName(value = "rag_api_key")
@Data
public class RagApiKey {

    /**
     * id (ASSIGN_ID snowflake).
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * Owner user id.
     */
    private Long userId;

    /**
     * User-chosen label, e.g. "本地 Agent".
     */
    private String keyName;

    /**
     * SHA-256 hex of the plaintext key; the only authentication lookup path.
     */
    private String keyHash;

    /**
     * First 8 chars of the plaintext, for display in the management list.
     */
    private String keyPrefix;

    /**
     * Create time.
     */
    private Date createTime;

    /**
     * Update time.
     */
    private Date updateTime;

    /**
     * Soft delete flag; a deleted key is revoked immediately.
     */
    @TableField
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

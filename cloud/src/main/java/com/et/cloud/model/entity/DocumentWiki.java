package com.et.cloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Wiki document.
 *
 * @TableName document_wiki
 */
@TableName(value = "document_wiki")
@Data
public class DocumentWiki {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * Document title.
     */
    private String title;

    /**
     * Saved document body.
     */
    private String content;

    /**
     * Short summary for list display.
     */
    private String summary;

    /**
     * Category.
     */
    private String category;

    /**
     * Tags JSON array.
     */
    private String tags;

    /**
     * Content format: plain / markdown. Old rows keep plain.
     */
    private String contentFormat;

    /**
     * Source type: NATIVE / UPLOAD / IMPORT / URL.
     */
    private String sourceType;

    /**
     * Original source url for tracing.
     */
    private String sourceUrl;

    /**
     * Content fingerprint (md5 hex). RAG reserved, server-managed later.
     */
    private String contentHash;

    /**
     * Content version, +1 per edit. RAG reserved, server-managed later.
     */
    private Integer contentVersion;

    /**
     * Visibility: PRIVATE / SPACE / PUBLIC. RAG reserved, server-managed later.
     */
    private String visibility;

    /**
     * Extensible metadata JSON. RAG reserved.
     */
    private String metadataJson;

    /**
     * Creator user id.
     */
    private Long userId;

    /**
     * Wiki space id.
     */
    private Long spaceId;

    /**
     * Wiki folder id. Null means the space root.
     */
    private Long folderId;

    /**
     * View count.
     */
    private Long viewCount;

    /**
     * Create time.
     */
    private Date createTime;

    /**
     * Edit time.
     */
    private Date editTime;

    /**
     * Update time.
     */
    private Date updateTime;

    /**
     * Logical delete time.
     */
    private Date deleteTime;

    /**
     * User who logically deleted this document.
     */
    private Long deleteBy;

    /**
     * Soft delete flag.
     */
    @TableField
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

package com.et.cloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Wiki attachment. Image is the first attachment type; the table is the seed
 * of the wiki file module (future PDF/Word for RAG).
 *
 * @TableName wiki_attachment
 */
@TableName(value = "wiki_attachment")
@Data
public class WikiAttachment {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Wiki space id.
     */
    private Long wikiSpaceId;

    /**
     * Linked document id. Null means not linked yet (e.g. pasted before save).
     */
    private Long documentId;

    /**
     * File name.
     */
    private String fileName;

    /**
     * File url.
     */
    private String url;

    /**
     * File size in bytes.
     */
    private Long fileSize;

    /**
     * Mime type.
     */
    private String mimeType;

    /**
     * File content hash (md5 hex). Reserved for dedup / RAG.
     */
    private String fileHash;

    /**
     * Uploader user id.
     */
    private Long userId;

    /**
     * Logical delete time.
     */
    private Date deleteTime;

    /**
     * User who logically deleted this attachment.
     */
    private Long deleteBy;

    /**
     * Create time.
     */
    private Date createTime;

    /**
     * Update time.
     */
    private Date updateTime;

    /**
     * Soft delete flag.
     */
    @TableField
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

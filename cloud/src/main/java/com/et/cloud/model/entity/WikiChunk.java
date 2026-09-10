package com.et.cloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Wiki RAG chunk. One row per semantic slice of a Markdown document.
 *
 * @TableName wiki_chunk
 */
@TableName(value = "wiki_chunk")
@Data
public class WikiChunk {

    public static final String STATUS_ACTIVE = "ACTIVE";

    public static final String STATUS_INVALID = "INVALID";

    /**
     * id (ASSIGN_ID snowflake).
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * document_wiki.id.
     */
    private Long docId;

    /**
     * wiki_space.id, redundant for permission filtering and bulk invalidation.
     */
    private Long spaceId;

    /**
     * Content version of the source document when this chunk was built.
     */
    private Integer contentVersion;

    /**
     * Zero-based index of the chunk inside its document, for jump positioning.
     */
    private Integer chunkIndex;

    /**
     * Heading path, e.g. "三、补助标准 > (二)发放方式".
     */
    private String chunkHeading;

    /**
     * Chunk text.
     */
    private String chunkText;

    /**
     * Document title, redundant to avoid joins on the hot retrieval path.
     */
    private String docTitle;

    /**
     * Government document number extracted by regex, nullable.
     */
    private String docNumber;

    /**
     * float32 little-endian serialized embedding vector.
     */
    private byte[] embedding;

    /**
     * ACTIVE / INVALID.
     */
    private String status;

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

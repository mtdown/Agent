package com.et.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.et.cloud.model.entity.WikiChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @Entity com.et.cloud.model.entity.WikiChunk
 */
public interface WikiChunkMapper extends BaseMapper<WikiChunk> {

    @Update("UPDATE wiki_chunk SET status = 'INVALID', updateTime = NOW() WHERE docId = #{docId} AND status = 'ACTIVE'")
    int invalidateByDocId(@Param("docId") Long docId);

    @Update("UPDATE wiki_chunk SET status = 'INVALID', updateTime = NOW() WHERE spaceId = #{spaceId} AND status = 'ACTIVE'")
    int invalidateBySpaceId(@Param("spaceId") Long spaceId);

    @Update("UPDATE wiki_chunk SET status = 'ACTIVE', updateTime = NOW() WHERE docId = #{docId} AND contentVersion = #{contentVersion} AND status = 'INVALID'")
    int reactivateByDocIdAndVersion(@Param("docId") Long docId, @Param("contentVersion") Integer contentVersion);

    @Update("UPDATE wiki_chunk SET spaceId = #{spaceId}, updateTime = NOW() WHERE docId = #{docId} AND status = 'ACTIVE'")
    int moveByDocId(@Param("docId") Long docId, @Param("spaceId") Long spaceId);

    @Delete("DELETE FROM wiki_chunk WHERE docId = #{docId}")
    int physicallyDeleteByDocId(@Param("docId") Long docId);

    @Delete("DELETE FROM wiki_chunk WHERE spaceId = #{spaceId}")
    int physicallyDeleteBySpaceId(@Param("spaceId") Long spaceId);

    @Update("UPDATE wiki_chunk c SET c.status = 'INVALID', c.updateTime = NOW() " +
            "WHERE c.status = 'ACTIVE' AND NOT EXISTS (" +
            "SELECT 1 FROM document_wiki d WHERE d.id = c.docId AND d.isDelete = 0)")
    int invalidateOrphans();

    @org.apache.ibatis.annotations.Select("SELECT DISTINCT docId FROM wiki_chunk WHERE spaceId = #{spaceId} AND status = 'ACTIVE'")
    List<Long> selectActiveDocIdsBySpaceId(@Param("spaceId") Long spaceId);
}

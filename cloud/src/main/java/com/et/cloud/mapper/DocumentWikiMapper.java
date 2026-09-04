package com.et.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.et.cloud.model.entity.DocumentWiki;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/**
 * @Entity com.et.cloud.model.entity.DocumentWiki
 */
public interface DocumentWikiMapper extends BaseMapper<DocumentWiki> {

    @Select("SELECT * FROM document_wiki WHERE id = #{id}")
    DocumentWiki selectByIdIncludeDeleted(@Param("id") Long id);

    @Select("SELECT * FROM document_wiki WHERE spaceId = #{spaceId} AND isDelete = 1 ORDER BY deleteTime DESC, id DESC")
    List<DocumentWiki> selectDeletedBySpaceId(@Param("spaceId") Long spaceId);

    @Update("UPDATE document_wiki SET isDelete = 1, deleteTime = #{deleteTime}, deleteBy = #{deleteBy} WHERE id = #{id}")
    int logicalDeleteById(@Param("id") Long id, @Param("deleteTime") Date deleteTime, @Param("deleteBy") Long deleteBy);

    @Update("UPDATE document_wiki SET isDelete = 1, deleteTime = #{deleteTime}, deleteBy = #{deleteBy} WHERE spaceId = #{spaceId}")
    int logicalDeleteBySpaceId(@Param("spaceId") Long spaceId, @Param("deleteTime") Date deleteTime, @Param("deleteBy") Long deleteBy);

    @Update("UPDATE document_wiki SET isDelete = 1, deleteTime = #{deleteTime}, deleteBy = #{deleteBy} WHERE folderId IN (${folderIds})")
    int logicalDeleteByFolderIds(@Param("folderIds") String folderIds, @Param("deleteTime") Date deleteTime, @Param("deleteBy") Long deleteBy);

    @Update("UPDATE document_wiki SET isDelete = 0, deleteTime = NULL, deleteBy = NULL WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

    @Update("UPDATE document_wiki SET isDelete = 0, deleteTime = NULL, deleteBy = NULL WHERE spaceId = #{spaceId}")
    int restoreBySpaceId(@Param("spaceId") Long spaceId);

    @Update("UPDATE document_wiki SET isDelete = 0, deleteTime = NULL, deleteBy = NULL WHERE folderId IN (${folderIds})")
    int restoreByFolderIds(@Param("folderIds") String folderIds);

    @Update("UPDATE document_wiki SET folderId = NULL WHERE id = #{id}")
    int moveToRoot(@Param("id") Long id);

    @Delete("DELETE FROM document_wiki WHERE id = #{id}")
    int physicallyDeleteById(@Param("id") Long id);

    @Delete("DELETE FROM document_wiki WHERE spaceId = #{spaceId}")
    int physicallyDeleteBySpaceId(@Param("spaceId") Long spaceId);

    @Delete("DELETE FROM document_wiki WHERE folderId IN (${folderIds})")
    int physicallyDeleteByFolderIds(@Param("folderIds") String folderIds);
}

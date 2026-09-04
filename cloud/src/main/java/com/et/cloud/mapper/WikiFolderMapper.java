package com.et.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.et.cloud.model.entity.WikiFolder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

public interface WikiFolderMapper extends BaseMapper<WikiFolder> {

    @Select("SELECT * FROM wiki_folder WHERE id = #{id}")
    WikiFolder selectByIdIncludeDeleted(@Param("id") Long id);

    @Select("SELECT * FROM wiki_folder WHERE spaceId = #{spaceId}")
    List<WikiFolder> selectAllBySpaceId(@Param("spaceId") Long spaceId);

    @Select("SELECT * FROM wiki_folder WHERE spaceId = #{spaceId} AND isDelete = 1 ORDER BY deleteTime DESC, id DESC")
    List<WikiFolder> selectDeletedBySpaceId(@Param("spaceId") Long spaceId);

    @Update("UPDATE wiki_folder SET isDelete = 1, deleteTime = #{deleteTime}, deleteBy = #{deleteBy} WHERE id IN (${folderIds})")
    int logicalDeleteByIds(@Param("folderIds") String folderIds, @Param("deleteTime") Date deleteTime, @Param("deleteBy") Long deleteBy);

    @Update("UPDATE wiki_folder SET isDelete = 1, deleteTime = #{deleteTime}, deleteBy = #{deleteBy} WHERE spaceId = #{spaceId}")
    int logicalDeleteBySpaceId(@Param("spaceId") Long spaceId, @Param("deleteTime") Date deleteTime, @Param("deleteBy") Long deleteBy);

    @Update("UPDATE wiki_folder SET isDelete = 0, deleteTime = NULL, deleteBy = NULL WHERE id IN (${folderIds})")
    int restoreByIds(@Param("folderIds") String folderIds);

    @Update("UPDATE wiki_folder SET isDelete = 0, deleteTime = NULL, deleteBy = NULL WHERE spaceId = #{spaceId}")
    int restoreBySpaceId(@Param("spaceId") Long spaceId);

    @Update("UPDATE wiki_folder SET parentId = NULL WHERE id = #{id}")
    int moveToRoot(@Param("id") Long id);

    @Delete("DELETE FROM wiki_folder WHERE id IN (${folderIds})")
    int physicallyDeleteByIds(@Param("folderIds") String folderIds);

    @Delete("DELETE FROM wiki_folder WHERE spaceId = #{spaceId}")
    int physicallyDeleteBySpaceId(@Param("spaceId") Long spaceId);
}

package com.et.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.et.cloud.model.entity.WikiSpace;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WikiSpaceMapper extends BaseMapper<WikiSpace> {

    @Select("SELECT * FROM wiki_space WHERE id = #{id}")
    WikiSpace selectByIdIncludeDeleted(@Param("id") Long id);

    @Update("UPDATE wiki_space SET isDelete = 0 WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

    @Delete("DELETE FROM wiki_space WHERE id = #{id}")
    int physicallyDeleteById(@Param("id") Long id);
}

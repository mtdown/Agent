package com.et.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.et.cloud.model.entity.WikiSpaceUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface WikiSpaceUserMapper extends BaseMapper<WikiSpaceUser> {

    @Update("UPDATE wiki_space_user SET isDelete = 0, spaceRole = #{spaceRole} WHERE spaceId = #{spaceId} AND userId = #{userId}")
    int restoreMember(@Param("spaceId") Long spaceId, @Param("userId") Long userId, @Param("spaceRole") String spaceRole);

    @Delete("DELETE FROM wiki_space_user WHERE spaceId = #{spaceId}")
    int physicallyDeleteBySpaceId(@Param("spaceId") Long spaceId);
}

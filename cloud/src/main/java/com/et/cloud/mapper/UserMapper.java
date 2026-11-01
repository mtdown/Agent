package com.et.cloud.mapper;

import com.et.cloud.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author origin
* @description 针对表【user(用户)】的数据库操作Mapper
* @createDate 2025-09-25 12:08:44
* @Entity com.et.cloud.model.entity.User
*/
public interface UserMapper extends BaseMapper<User> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM user WHERE id = #{id} AND isDelete = 0 FOR UPDATE")
    User lockActiveById(@org.apache.ibatis.annotations.Param("id") Long id);

}





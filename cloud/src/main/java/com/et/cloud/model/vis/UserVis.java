package com.et.cloud.model.vis;


import com.et.cloud.model.entity.User;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

@Data
public class UserVis implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 4242078602479696382L;

    public static UserVis objToVo(User user) {
        if (user == null) {
            return null;
        }
        UserVis userVO = new UserVis();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }
}

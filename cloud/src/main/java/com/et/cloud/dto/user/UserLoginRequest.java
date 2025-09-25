package com.et.cloud.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 5866014552348639190L;

    /**
     * 账号
     */
    private String userAccount;
    /**
     * 密码
     */
    private String userPassword;
}

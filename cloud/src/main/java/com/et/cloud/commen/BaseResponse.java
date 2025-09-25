package com.et.cloud.commen;

import com.et.cloud.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * @author leikooo
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    //不需要填写message的情况
    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    //这样就能通过errorcode给出错误返回值
    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}


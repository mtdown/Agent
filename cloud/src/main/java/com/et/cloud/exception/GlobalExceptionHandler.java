package com.et.cloud.exception;
import com.et.cloud.commen.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.et.cloud.commen.ResultUtils;
/**
 * @author leikooo
 */
@RestControllerAdvice
//环绕切面
@Slf4j
public class GlobalExceptionHandler {

//    @ExceptionHandler(NotLoginException.class)
//    public BaseResponse<?> notLoginException(NotLoginException e) {
//        log.error("NotLoginException", e);
//        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, e.getMessage());
//    }
//
//    @ExceptionHandler(NotPermissionException.class)
//    public BaseResponse<?> notPermissionExceptionHandler(NotPermissionException e) {
//        log.error("NotPermissionException", e);
//        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, e.getMessage());
//    }

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NullPointerException.class)
    public BaseResponse<?> nullPointerExceptionHandler(NullPointerException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR);
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "运行时异常");
    }

    @ExceptionHandler(Exception.class)
    public BaseResponse<?> exceptionHandler(Exception e) {
        log.error("Exception", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}


package com.et.cloud.exception;

import com.et.cloud.commen.BaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void requestMethodMismatchReturnsParamsError() {
        BaseResponse<?> response = handler.requestBindingExceptionHandler(
                new HttpRequestMethodNotSupportedException("GET", new String[]{"POST"}));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), response.getCode());
    }

    @Test
    void missingMultipartFileReturnsParamsError() {
        BaseResponse<?> response = handler.requestBindingExceptionHandler(
                new MissingServletRequestPartException("file"));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), response.getCode());
    }
}

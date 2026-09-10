package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.model.entity.User;
import com.et.cloud.rag.RagApiKeyCreatedView;
import com.et.cloud.rag.RagApiKeyService;
import com.et.cloud.rag.RagApiKeyView;
import com.et.cloud.service.UserService;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * In-app API key management for the open RAG API (login required).
 * The plaintext key is returned only by the create endpoint.
 */
@RestController
@RequestMapping("/rag/key")
public class RagApiKeyController {

    @Resource
    private UserService userService;

    @Resource
    private RagApiKeyService ragApiKeyService;

    @Data
    public static class KeyCreateRequest {
        private String keyName;
    }

    @Data
    public static class KeyDeleteRequest {
        private Long id;
    }

    @PostMapping("/create")
    public BaseResponse<RagApiKeyCreatedView> create(@RequestBody KeyCreateRequest request,
                                                     HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        String keyName = request == null ? null : request.getKeyName();
        return ResultUtils.success(ragApiKeyService.create(loginUser, keyName));
    }

    @GetMapping("/list")
    public BaseResponse<List<RagApiKeyView>> list(HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(ragApiKeyService.list(loginUser));
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> delete(@RequestBody KeyDeleteRequest request,
                                        HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null || request.getId() == null || request.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpServletRequest);
        boolean deleted = ragApiKeyService.delete(loginUser, request.getId());
        ThrowUtils.throwIf(!deleted, ErrorCode.NOT_FOUND_ERROR, "Key 不存在或无权操作");
        return ResultUtils.success(true);
    }
}

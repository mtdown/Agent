package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.dto.documentWiki.DocumentWikiBatchUrlImportRequest;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.model.dto.BatchImportItemResult;
import com.et.cloud.model.entity.User;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiBatchImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

/**
 * Batch import endpoints. Both return one result per submitted item.
 */
@RestController
@RequestMapping("/documentWiki/batch")
public class DocumentWikiBatchController {

    @Resource
    private UserService userService;

    @Resource
    private WikiBatchImportService wikiBatchImportService;

    @PostMapping("/url")
    public BaseResponse<List<BatchImportItemResult>> importUrls(
            @RequestBody DocumentWikiBatchUrlImportRequest request,
            HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getSpaceId() == null || request.getSpaceId() <= 0,
                ErrorCode.PARAMS_ERROR, "空间不能为空");
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(wikiBatchImportService.importUrls(request, loginUser));
    }

    @PostMapping("/file")
    public BaseResponse<List<BatchImportItemResult>> importFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("spaceId") Long spaceId,
            @RequestParam(value = "folderId", required = false) Long folderId,
            HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(files == null || files.length == 0, ErrorCode.PARAMS_ERROR, "请至少选择一个文件");
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间不能为空");
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(
                wikiBatchImportService.importFiles(spaceId, folderId, Arrays.asList(files), loginUser));
    }

    /**
     * One JSON corpus file becomes one document per entry. Unlike {@code /file} there is no entry
     * count limit: the corpus is submitted as a single file, so only its size is bounded.
     */
    @PostMapping("/json")
    public BaseResponse<List<BatchImportItemResult>> importJson(
            @RequestParam("file") MultipartFile file,
            @RequestParam("spaceId") Long spaceId,
            @RequestParam(value = "folderId", required = false) Long folderId,
            HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "请选择要导入的 JSON 文件");
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间不能为空");
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(wikiBatchImportService.importJson(spaceId, folderId, file, loginUser));
    }
}

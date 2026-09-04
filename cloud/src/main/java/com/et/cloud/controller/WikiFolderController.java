package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.DeleteRequest;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.dto.wikifolder.WikiFolderAddRequest;
import com.et.cloud.dto.wikifolder.WikiFolderMoveRequest;
import com.et.cloud.dto.wikifolder.WikiFolderRenameRequest;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.vis.WikiFolderVis;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiFolderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/wikiFolder")
public class WikiFolderController {

    @Resource
    private WikiFolderService wikiFolderService;

    @Resource
    private UserService userService;

    @PostMapping("/add")
    public BaseResponse<Long> addFolder(@RequestBody WikiFolderAddRequest addRequest,
                                        HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiFolderService.createFolder(addRequest.getSpaceId(), addRequest.getParentId(), addRequest.getName(), loginUser));
    }

    @PostMapping("/rename")
    public BaseResponse<Boolean> renameFolder(@RequestBody WikiFolderRenameRequest renameRequest,
                                              HttpServletRequest request) {
        ThrowUtils.throwIf(renameRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiFolderService.renameFolder(renameRequest.getId(), renameRequest.getName(), loginUser));
    }

    @PostMapping("/move")
    public BaseResponse<Boolean> moveFolder(@RequestBody WikiFolderMoveRequest moveRequest,
                                            HttpServletRequest request) {
        ThrowUtils.throwIf(moveRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiFolderService.moveFolder(moveRequest.getId(), moveRequest.getParentId(), loginUser));
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteFolder(@RequestBody DeleteRequest deleteRequest,
                                              HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiFolderService.deleteFolderTree(deleteRequest.getId(), loginUser));
    }

    @GetMapping("/tree/list")
    public BaseResponse<List<WikiFolderVis>> listFolderTree(Long spaceId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiFolderService.listFolderTree(spaceId, loginUser));
    }
}

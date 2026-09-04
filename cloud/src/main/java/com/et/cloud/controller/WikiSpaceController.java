package com.et.cloud.controller;

import com.et.cloud.annotation.AuthCheck;
import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.dto.wikispace.WikiSpaceConfirmRequest;
import com.et.cloud.dto.wikispace.WikiTeamMemberAddRequest;
import com.et.cloud.dto.wikispace.WikiTeamMemberDeleteRequest;
import com.et.cloud.dto.wikispace.WikiTeamSpaceAddRequest;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.model.constant.UserConstant;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.vis.WikiSpaceUserVis;
import com.et.cloud.model.vis.WikiSpaceVis;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiSpaceService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/wikiSpace")
public class WikiSpaceController {

    @Resource
    private WikiSpaceService wikiSpaceService;

    @Resource
    private UserService userService;

    @GetMapping("/list/visible")
    public BaseResponse<List<WikiSpaceVis>> listVisibleSpace(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiSpaceService.listVisibleSpaceVis(loginUser));
    }

    @PostMapping("/add/team")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addTeamSpace(@RequestBody WikiTeamSpaceAddRequest addRequest,
                                           HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiSpaceService.createTeamSpace(addRequest.getName(), loginUser));
    }

    @GetMapping("/list/manage/team")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<WikiSpaceVis>> listManageTeamSpaces(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiSpaceService.listManageTeamSpaces(loginUser));
    }

    @PostMapping("/member/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addTeamMember(@RequestBody WikiTeamMemberAddRequest addRequest,
                                            HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiSpaceService.addTeamMember(
                addRequest.getSpaceId(),
                addRequest.getUserId(),
                addRequest.getSpaceRole(),
                loginUser
        ));
    }

    @PostMapping("/member/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> removeTeamMember(@RequestBody WikiTeamMemberDeleteRequest deleteRequest,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiSpaceService.removeTeamMember(deleteRequest.getSpaceId(), deleteRequest.getUserId(), loginUser));
    }

    @GetMapping("/member/list")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<WikiSpaceUserVis>> listTeamMembers(Long spaceId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiSpaceService.listTeamMembers(spaceId, loginUser));
    }

    @PostMapping("/exit")
    public BaseResponse<Boolean> exitTeamSpace(@RequestBody WikiSpaceConfirmRequest exitRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(exitRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiSpaceService.exitTeamSpace(exitRequest.getId(), loginUser));
    }

    @PostMapping("/delete/team")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteTeamSpace(@RequestBody WikiSpaceConfirmRequest deleteRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiSpaceService.deleteTeamSpace(deleteRequest.getId(), deleteRequest.getConfirm(), loginUser));
    }

    @PostMapping("/restore/team")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> restoreTeamSpace(@RequestBody WikiSpaceConfirmRequest restoreRequest,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(restoreRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiSpaceService.restoreTeamSpace(restoreRequest.getId(), loginUser));
    }

    @PostMapping("/permanentDelete/team")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> permanentDeleteTeamSpace(@RequestBody WikiSpaceConfirmRequest deleteRequest,
                                                          HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wikiSpaceService.permanentDeleteTeamSpace(deleteRequest.getId(), deleteRequest.getConfirm(), loginUser));
    }
}

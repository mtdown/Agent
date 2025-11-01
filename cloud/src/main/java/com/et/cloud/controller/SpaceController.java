package com.et.cloud.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.et.cloud.annotation.AuthCheck;
import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.DeleteRequest;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.config.CosClientConfig;
import com.et.cloud.dto.space.*;
//import com.et.cloud.enums.SpaceReviewStatusEnum;
import com.et.cloud.enums.SpaceLevelEnum;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.manager.CosManager;
import com.et.cloud.manager.auth.SpaceUserAuthManager;
import com.et.cloud.model.constant.UserConstant;
import com.et.cloud.model.entity.Space;
import com.et.cloud.model.entity.User;
//import com.et.cloud.model.vis.SpaceTagCategory;
import com.et.cloud.model.vis.SpaceVis;
import com.et.cloud.service.SpaceService;
import com.et.cloud.service.UserService;
import com.et.cloud.dto.space.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

//import com.et.cloud.dto.space.SpaceUpdateRequest;
@RestController
@RequestMapping("/space")
@Slf4j
public class SpaceController {
    @Resource
    private CosManager cosManager;
    //    private final CosManager cosManager;
    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        long newId = spaceService.addSpace(spaceAddRequest, loginUser);
        return ResultUtils.success(newId);
    }

    //删除空间
    @PostMapping("/delete")
//    从HTTP请求的里面的body提取出需要的请求数据
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest,
                                               HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //问题1，这里面的userservice类里面的用户信息是来自哪个报文段
        //很显然是http请求，返回的也是uer对象，但说实话，这个函数名字太糟糕了，好像在返回用户试图的对象数据一样
        User loginUser=userService.getLoginUser(request);
        //指出要删除谁
        Long id = deleteRequest.getId();
        Space oldSpace=spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace==null,ErrorCode.NOT_FOUND_ERROR);

        if(!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        Boolean result = spaceService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(result);
    }

    //更新空间
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest, HttpServletRequest request) {
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
//        自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);
//       数据校验
        spaceService.vaildSpace(space,false);

        // 判断是否存在
        long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        // 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    //空间获取
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        //犯错了，不应该相信用户发来的id
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(space);
    }

    /**
     * 根据 id 获取空间（封装类）
     */
    @GetMapping("/get/vis")
    public BaseResponse<SpaceVis> getSpaceVisById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
//        登录用户信息获取
        SpaceVis spaceVis = spaceService.getspaceVis(space, request);
        User loginUser = userService.getLoginUser(request);
//        设置指令
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space,loginUser);
        spaceVis.setPermissionList(permissionList);
        // 获取封装类
        return ResultUtils.success(spaceVis);
    }


    /**
     * 分页获取空间列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        //普通用户只能看到审核通过的数据,原本QueryRequest是不带satus标记的，就不会查询，这里增加一行实现了查询效果
//        spaceQueryRequest.setReviewStatus(SpaceReviewStatusEnum.PASS.getValue());
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spacePage);
    }

    /**
     * 分页获取空间列表（封装类）
     */
    @PostMapping("/list/page/vis")
    public BaseResponse<Page<SpaceVis>> listSpaceVisByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,HttpServletRequest request) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

//        spaceQueryRequest.setReviewStatus(SpaceReviewStatusEnum.PASS.getValue());
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        // 获取封装类
        return ResultUtils.success(spaceService.getSpaceVisPage(spacePage, request));
    }

    /**
     * 编辑空间（给用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        if (spaceEditRequest == null || spaceEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);
        spaceService.fillSpaceBySpaceLevel(space);

        // 设置编辑时间,完全没有想到的部分
        space.setEditTime(new Date());

        // 数据校验
        spaceService.vaildSpace(space,false);
        User loginUser = userService.getLoginUser(request);

        // 判断是否存在,这里先获取空间，在把这个空间的id放过去是为了拒绝用户给的信息，转而使用自己的信息
        long id = spaceEditRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(result);
    }
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values())
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()
                ))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }



}

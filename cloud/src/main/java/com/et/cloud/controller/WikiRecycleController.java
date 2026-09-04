package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.dto.wikirecycle.WikiRecycleActionRequest;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.mapper.DocumentWikiMapper;
import com.et.cloud.mapper.UserMapper;
import com.et.cloud.mapper.WikiFolderMapper;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiFolder;
import com.et.cloud.model.vis.UserVis;
import com.et.cloud.model.vis.WikiRecycleItemVis;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiCacheManager;
import com.et.cloud.service.WikiFolderService;
import com.et.cloud.service.WikiSpaceService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/wikiRecycle")
public class WikiRecycleController {

    @Resource
    private UserService userService;

    @Resource
    private WikiSpaceService wikiSpaceService;

    @Resource
    private WikiFolderService wikiFolderService;

    @Resource
    private DocumentWikiService documentWikiService;

    @Resource
    private DocumentWikiMapper documentWikiMapper;

    @Resource
    private WikiFolderMapper wikiFolderMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private WikiCacheManager wikiCacheManager;

    @GetMapping("/list")
    public BaseResponse<List<WikiRecycleItemVis>> list(Long spaceId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        wikiSpaceService.requireVisibleSpace(spaceId, loginUser);
        List<WikiRecycleItemVis> items = new ArrayList<>();
        for (DocumentWiki document : documentWikiMapper.selectDeletedBySpaceId(spaceId)) {
            WikiRecycleItemVis item = new WikiRecycleItemVis();
            item.setItemType("document");
            item.setItemId(document.getId());
            item.setSpaceId(document.getSpaceId());
            item.setParentId(document.getFolderId());
            item.setTitle(document.getTitle());
            item.setDeleteTime(document.getDeleteTime());
            item.setDeleteBy(document.getDeleteBy());
            fillDeleteUser(item);
            items.add(item);
        }
        for (WikiFolder folder : wikiFolderMapper.selectDeletedBySpaceId(spaceId)) {
            WikiRecycleItemVis item = new WikiRecycleItemVis();
            item.setItemType("folder");
            item.setItemId(folder.getId());
            item.setSpaceId(folder.getSpaceId());
            item.setParentId(folder.getParentId());
            item.setTitle(folder.getName());
            item.setDeleteTime(folder.getDeleteTime());
            item.setDeleteBy(folder.getDeleteBy());
            fillDeleteUser(item);
            items.add(item);
        }
        items.sort(Comparator.comparing(WikiRecycleItemVis::getDeleteTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return ResultUtils.success(items);
    }

    @PostMapping("/restore")
    public BaseResponse<Boolean> restore(@RequestBody WikiRecycleActionRequest restoreRequest,
                                         HttpServletRequest request) {
        ThrowUtils.throwIf(restoreRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        wikiSpaceService.requireVisibleSpace(restoreRequest.getSpaceId(), loginUser);
        if ("folder".equals(restoreRequest.getItemType())) {
            return ResultUtils.success(wikiFolderService.restoreFolderTree(restoreRequest.getItemId(), loginUser));
        }
        DocumentWiki document = documentWikiService.getByIdIncludeDeleted(restoreRequest.getItemId());
        ThrowUtils.throwIf(document == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!Objects.equals(document.getIsDelete(), 1), ErrorCode.PARAMS_ERROR, "该文档不在回收站中");
        wikiSpaceService.requireVisibleSpace(document.getSpaceId(), loginUser);
        Boolean result = documentWikiService.restore(document.getId());
        wikiCacheManager.clearDocument(document.getSpaceId(), document.getId());
        return ResultUtils.success(result);
    }

    @PostMapping("/permanentDelete")
    public BaseResponse<Boolean> permanentDelete(@RequestBody WikiRecycleActionRequest deleteRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || !Boolean.TRUE.equals(deleteRequest.getConfirm()), ErrorCode.PARAMS_ERROR, "请确认永久删除");
        User loginUser = userService.getLoginUser(request);
        wikiSpaceService.requireVisibleSpace(deleteRequest.getSpaceId(), loginUser);
        if ("folder".equals(deleteRequest.getItemType())) {
            return ResultUtils.success(wikiFolderService.permanentDeleteFolderTree(deleteRequest.getItemId(), loginUser));
        }
        DocumentWiki document = documentWikiService.getByIdIncludeDeleted(deleteRequest.getItemId());
        ThrowUtils.throwIf(document == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!Objects.equals(document.getIsDelete(), 1), ErrorCode.PARAMS_ERROR, "该文档不在回收站中");
        wikiSpaceService.requireVisibleSpace(document.getSpaceId(), loginUser);
        Boolean result = documentWikiService.permanentDelete(document.getId());
        wikiCacheManager.clearSpace(document.getSpaceId());
        return ResultUtils.success(result);
    }

    private void fillDeleteUser(WikiRecycleItemVis item) {
        if (item.getDeleteBy() == null) {
            return;
        }
        User user = userMapper.selectById(item.getDeleteBy());
        if (user == null) {
            return;
        }
        UserVis userVis = new UserVis();
        BeanUtils.copyProperties(user, userVis);
        item.setDeleteUser(userVis);
    }
}

package com.et.cloud.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.DeleteRequest;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.dto.documentWiki.DocumentWikiAddRequest;
import com.et.cloud.dto.documentWiki.DocumentWikiEditRequest;
import com.et.cloud.dto.documentWiki.DocumentWikiMoveRequest;
import com.et.cloud.dto.documentWiki.DocumentWikiQueryRequest;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiFolder;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.model.vis.DocumentWikiVis;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiCacheManager;
import com.et.cloud.service.WikiFolderService;
import com.et.cloud.service.WikiSpaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/documentWiki")
@Slf4j
public class DocumentWikiController {

    private static final int MAX_PAGE_SIZE = 20;

    private static final String DETAIL_CACHE_KEY_PREFIX = "agentWiki:documentWiki:detail:";

    private static final String LIST_CACHE_KEY_PREFIX = "agentWiki:documentWiki:list:";

    @Resource
    private DocumentWikiService documentWikiService;

    @Resource
    private UserService userService;

    @Resource
    private WikiSpaceService wikiSpaceService;

    @Resource
    private WikiFolderService wikiFolderService;

    @Resource
    private WikiCacheManager wikiCacheManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("/add")
    public BaseResponse<Long> addDocumentWiki(@RequestBody DocumentWikiAddRequest documentWikiAddRequest,
                                              HttpServletRequest request) {
        ThrowUtils.throwIf(documentWikiAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        DocumentWiki documentWiki = new DocumentWiki();
        BeanUtils.copyProperties(documentWikiAddRequest, documentWiki);
        documentWiki.setTags(JSONUtil.toJsonStr(documentWikiAddRequest.getTags()));
        WikiSpace wikiSpace = requireVisibleSpace(documentWikiAddRequest.getSpaceId(), request);
        if (documentWikiAddRequest.getFolderId() != null) {
            WikiFolder folder = wikiFolderService.requireVisibleFolder(documentWikiAddRequest.getFolderId(), wikiSpace.getId(), loginUser);
            documentWiki.setFolderId(folder.getId());
        }
        documentWiki.setSpaceId(wikiSpace.getId());
        if (StrUtil.isBlank(documentWiki.getSummary())) {
            documentWiki.setSummary(documentWikiService.buildSummary(documentWiki.getContent()));
        }
        documentWiki.setUserId(loginUser.getId());
        documentWiki.setViewCount(0L);
        documentWiki.setEditTime(new Date());
        documentWikiService.validDocumentWiki(documentWiki);
        boolean result = documentWikiService.save(documentWiki);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        wikiCacheManager.clearSpace(documentWiki.getSpaceId());
        return ResultUtils.success(documentWiki.getId());
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteDocumentWiki(@RequestBody DeleteRequest deleteRequest,
                                                    HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        DocumentWiki oldDocumentWiki = documentWikiService.getById(id);
        ThrowUtils.throwIf(oldDocumentWiki == null, ErrorCode.NOT_FOUND_ERROR);
        documentWikiService.checkDocumentWikiVisible(loginUser, oldDocumentWiki);
        boolean result = documentWikiService.logicalDelete(id, loginUser.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        wikiCacheManager.clearDocument(oldDocumentWiki.getSpaceId(), id);
        return ResultUtils.success(true);
    }

    @GetMapping("/get/vis")
    public BaseResponse<DocumentWikiVis> getDocumentWikiVisById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        DocumentWiki sourceDocumentWiki = documentWikiService.getById(id);
        ThrowUtils.throwIf(sourceDocumentWiki == null, ErrorCode.NOT_FOUND_ERROR);
        documentWikiService.checkDocumentWikiVisible(loginUser, sourceDocumentWiki);
        String cacheKey = DETAIL_CACHE_KEY_PREFIX + sourceDocumentWiki.getSpaceId() + ":" + id;
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String cachedValue = valueOps.get(cacheKey);
        if (cachedValue != null) {
            DocumentWikiVis cachedDocumentWikiVis = JSONUtil.toBean(cachedValue, DocumentWikiVis.class);
            return ResultUtils.success(cachedDocumentWikiVis);
        }
        DocumentWikiVis documentWikiVis = documentWikiService.getDocumentWikiVis(sourceDocumentWiki, request);
        valueOps.set(cacheKey, JSONUtil.toJsonStr(documentWikiVis), 300 + RandomUtil.randomInt(0, 300), TimeUnit.SECONDS);
        return ResultUtils.success(documentWikiVis);
    }

    @PostMapping("/list/page/vis")
    public BaseResponse<Page<DocumentWikiVis>> listDocumentWikiVisByPage(@RequestBody DocumentWikiQueryRequest documentWikiQueryRequest,
                                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(documentWikiQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = documentWikiQueryRequest.getCurrent();
        long size = documentWikiQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > MAX_PAGE_SIZE, ErrorCode.PARAMS_ERROR);
        prepareVisibleDocumentWikiQuery(documentWikiQueryRequest, request);
        Page<DocumentWiki> documentWikiPage = documentWikiService.page(
                new Page<>(current, size),
                documentWikiService.getQueryWrapper(documentWikiQueryRequest)
        );
        return ResultUtils.success(documentWikiService.getDocumentWikiVisPage(documentWikiPage, request));
    }

    @PostMapping("/list/page/vis/cache")
    public BaseResponse<Page<DocumentWikiVis>> listDocumentWikiVisByPageWithCache(@RequestBody DocumentWikiQueryRequest documentWikiQueryRequest,
                                                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(documentWikiQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long size = documentWikiQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > MAX_PAGE_SIZE, ErrorCode.PARAMS_ERROR);
        prepareVisibleDocumentWikiQuery(documentWikiQueryRequest, request);
        String queryCondition = JSONUtil.toJsonStr(documentWikiQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String spaceKey = documentWikiQueryRequest.getSpaceId() == null ? "all" : String.valueOf(documentWikiQueryRequest.getSpaceId());
        String cacheKey = LIST_CACHE_KEY_PREFIX + spaceKey + ":" + hashKey;
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String cachedValue = valueOps.get(cacheKey);
        if (cachedValue != null) {
            Page<DocumentWikiVis> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }
        Page<DocumentWiki> documentWikiPage = documentWikiService.page(
                new Page<>(documentWikiQueryRequest.getCurrent(), size),
                documentWikiService.getQueryWrapper(documentWikiQueryRequest)
        );
        Page<DocumentWikiVis> documentWikiVisPage = documentWikiService.getDocumentWikiVisPage(documentWikiPage, request);
        valueOps.set(cacheKey, JSONUtil.toJsonStr(documentWikiVisPage), 300 + RandomUtil.randomInt(0, 300), TimeUnit.SECONDS);
        return ResultUtils.success(documentWikiVisPage);
    }

    @PostMapping("/edit")
    public BaseResponse<Boolean> editDocumentWiki(@RequestBody DocumentWikiEditRequest documentWikiEditRequest,
                                                  HttpServletRequest request) {
        if (documentWikiEditRequest == null || documentWikiEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = documentWikiEditRequest.getId();
        DocumentWiki oldDocumentWiki = documentWikiService.getById(id);
        ThrowUtils.throwIf(oldDocumentWiki == null, ErrorCode.NOT_FOUND_ERROR);
        documentWikiService.checkDocumentWikiVisible(loginUser, oldDocumentWiki);
        DocumentWiki documentWiki = new DocumentWiki();
        BeanUtils.copyProperties(documentWikiEditRequest, documentWiki);
        documentWiki.setSpaceId(oldDocumentWiki.getSpaceId());
        documentWiki.setFolderId(oldDocumentWiki.getFolderId());
        documentWiki.setTags(JSONUtil.toJsonStr(documentWikiEditRequest.getTags()));
        if (StrUtil.isBlank(documentWiki.getSummary())) {
            documentWiki.setSummary(documentWikiService.buildSummary(documentWiki.getContent()));
        }
        documentWiki.setEditTime(new Date());
        documentWikiService.validDocumentWiki(documentWiki);
        boolean result = documentWikiService.updateById(documentWiki);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        wikiCacheManager.clearDocument(oldDocumentWiki.getSpaceId(), id);
        return ResultUtils.success(true);
    }

    @PostMapping("/move")
    public BaseResponse<Boolean> moveDocumentWiki(@RequestBody DocumentWikiMoveRequest moveRequest,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(moveRequest == null || moveRequest.getId() == null || moveRequest.getTargetSpaceId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        DocumentWiki documentWiki = documentWikiService.getById(moveRequest.getId());
        ThrowUtils.throwIf(documentWiki == null, ErrorCode.NOT_FOUND_ERROR);
        documentWikiService.checkDocumentWikiVisible(loginUser, documentWiki);
        WikiSpace targetSpace = wikiSpaceService.requireVisibleSpace(moveRequest.getTargetSpaceId(), loginUser);
        if (moveRequest.getTargetFolderId() != null) {
            wikiFolderService.requireVisibleFolder(moveRequest.getTargetFolderId(), targetSpace.getId(), loginUser);
        }
        Long oldSpaceId = documentWiki.getSpaceId();
        documentWiki.setSpaceId(targetSpace.getId());
        documentWiki.setFolderId(moveRequest.getTargetFolderId());
        documentWiki.setEditTime(new Date());
        boolean result = documentWikiService.updateById(documentWiki);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        wikiCacheManager.clearDocument(oldSpaceId, documentWiki.getId());
        wikiCacheManager.clearDocument(targetSpace.getId(), documentWiki.getId());
        return ResultUtils.success(true);
    }

    @GetMapping("/root/list")
    public BaseResponse<java.util.List<DocumentWikiVis>> listRootDocumentWiki(Long spaceId, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        wikiSpaceService.requireVisibleSpace(spaceId, loginUser);
        java.util.List<DocumentWiki> documents = documentWikiService.lambdaQuery()
                .eq(DocumentWiki::getSpaceId, spaceId)
                .isNull(DocumentWiki::getFolderId)
                .eq(DocumentWiki::getIsDelete, 0)
                .orderByDesc(DocumentWiki::getEditTime)
                .list();
        java.util.List<DocumentWikiVis> visList = documents.stream()
                .map(documentWiki -> documentWikiService.getDocumentWikiVis(documentWiki, request))
                .collect(java.util.stream.Collectors.toList());
        return ResultUtils.success(visList);
    }

    private WikiSpace requireVisibleSpace(Long spaceId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return wikiSpaceService.requireVisibleSpace(spaceId, loginUser);
    }

    private void prepareVisibleDocumentWikiQuery(DocumentWikiQueryRequest documentWikiQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (documentWikiQueryRequest.getSpaceId() != null) {
            wikiSpaceService.requireVisibleSpace(documentWikiQueryRequest.getSpaceId(), loginUser);
        }
        documentWikiQueryRequest.setVisibleSpaceIds(wikiSpaceService.listVisibleSpaceIds(loginUser));
    }
}

package com.et.cloud.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.et.cloud.dto.documentWiki.DocumentWikiQueryRequest;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.mapper.DocumentWikiMapper;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.vis.DocumentWikiVis;
import com.et.cloud.model.vis.UserVis;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiSpaceService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class DocumentWikiServiceImpl extends ServiceImpl<DocumentWikiMapper, DocumentWiki>
        implements DocumentWikiService {

    private static final int MAX_TITLE_LENGTH = 128;

    private static final int MAX_SUMMARY_LENGTH = 512;

    private static final int MAX_CATEGORY_LENGTH = 64;

    private static final int MAX_CONTENT_LENGTH = 100000;

    private static final int SUMMARY_LENGTH = 160;

    @Resource
    private UserService userService;

    @Resource
    private WikiSpaceService wikiSpaceService;

    @Override
    public QueryWrapper<DocumentWiki> getQueryWrapper(DocumentWikiQueryRequest documentWikiQueryRequest) {
        QueryWrapper<DocumentWiki> queryWrapper = new QueryWrapper<>();
        if (documentWikiQueryRequest == null) {
            return queryWrapper;
        }
        Long id = documentWikiQueryRequest.getId();
        String title = documentWikiQueryRequest.getTitle();
        String summary = documentWikiQueryRequest.getSummary();
        List<String> tags = documentWikiQueryRequest.getTags();
        String searchText = documentWikiQueryRequest.getSearchText();
        String matchMode = documentWikiQueryRequest.getMatchMode();
        Long spaceId = documentWikiQueryRequest.getSpaceId();
        Long folderId = documentWikiQueryRequest.getFolderId();
        Long userId = documentWikiQueryRequest.getUserId();
        List<Long> visibleSpaceIds = documentWikiQueryRequest.getVisibleSpaceIds();
        String sortField = documentWikiQueryRequest.getSortField();
        String sortOrder = documentWikiQueryRequest.getSortOrder();

        if (StrUtil.isNotBlank(searchText)) {
            if ("title".equals(matchMode)) {
                queryWrapper.like("title", searchText);
            } else if ("content".equals(matchMode)) {
                queryWrapper.like("content", searchText);
            } else {
                queryWrapper.and(qw -> qw.like("title", searchText).or().like("content", searchText));
            }
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        if (folderId != null) {
            queryWrapper.eq("folderId", folderId);
        }
        if (visibleSpaceIds != null) {
            if (visibleSpaceIds.isEmpty()) {
                queryWrapper.apply("1 = 0");
            } else {
                queryWrapper.in("spaceId", visibleSpaceIds);
            }
        }
        queryWrapper.eq("isDelete", 0);
        queryWrapper.like(StrUtil.isNotBlank(title), "title", title);
        queryWrapper.like(StrUtil.isNotBlank(summary), "summary", summary);
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        queryWrapper.orderBy(StringUtils.isNotBlank(sortField), "ascend".equals(sortOrder), sortField);
        return queryWrapper;
    }

    @Override
    public DocumentWikiVis getDocumentWikiVis(DocumentWiki documentWiki, HttpServletRequest request) {
        DocumentWikiVis documentWikiVis = DocumentWikiVis.objToVis(documentWiki);
        if (documentWikiVis == null) {
            return null;
        }
        Long userId = documentWiki.getUserId();
        if (userId != null && userId > 0 && userService != null) {
            User user = userService.getById(userId);
            UserVis userVis = userService.getUserVis(user);
            documentWikiVis.setUser(userVis);
        }
        return documentWikiVis;
    }

    @Override
    public Page<DocumentWikiVis> getDocumentWikiVisPage(Page<DocumentWiki> documentWikiPage, HttpServletRequest request) {
        List<DocumentWiki> documentWikiList = documentWikiPage.getRecords();
        Page<DocumentWikiVis> documentWikiVisPage = new Page<>(
                documentWikiPage.getCurrent(),
                documentWikiPage.getSize(),
                documentWikiPage.getTotal()
        );
        if (CollUtil.isEmpty(documentWikiList)) {
            return documentWikiVisPage;
        }
        List<DocumentWikiVis> documentWikiVisList = new ArrayList<>();
        for (DocumentWiki documentWiki : documentWikiList) {
            documentWikiVisList.add(this.getDocumentWikiVis(documentWiki, request));
        }
        documentWikiVisPage.setRecords(documentWikiVisList);
        return documentWikiVisPage;
    }

    @Override
    public void validDocumentWiki(DocumentWiki documentWiki) {
        ThrowUtils.throwIf(documentWiki == null, ErrorCode.PARAMS_ERROR);
        String title = documentWiki.getTitle();
        String content = documentWiki.getContent();
        String summary = documentWiki.getSummary();
        String category = documentWiki.getCategory();
        ThrowUtils.throwIf(StrUtil.isBlank(title), ErrorCode.PARAMS_ERROR, "标题不能为空");
        ThrowUtils.throwIf(title.length() > MAX_TITLE_LENGTH, ErrorCode.PARAMS_ERROR, "标题过长");
        ThrowUtils.throwIf(StrUtil.isBlank(content), ErrorCode.PARAMS_ERROR, "正文不能为空");
        ThrowUtils.throwIf(content.length() > MAX_CONTENT_LENGTH, ErrorCode.PARAMS_ERROR, "正文过长");
        ThrowUtils.throwIf(StrUtil.isNotBlank(summary) && summary.length() > MAX_SUMMARY_LENGTH, ErrorCode.PARAMS_ERROR, "摘要过长");
        ThrowUtils.throwIf(category != null && category.length() > MAX_CATEGORY_LENGTH, ErrorCode.PARAMS_ERROR, "分类过长");
        ThrowUtils.throwIf(documentWiki.getSpaceId() == null || documentWiki.getSpaceId() <= 0, ErrorCode.PARAMS_ERROR, "空间不能为空");
    }

    @Override
    public void checkDocumentWikiAuth(User loginUser, DocumentWiki documentWiki) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(documentWiki == null, ErrorCode.NOT_FOUND_ERROR);
        if (!documentWiki.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    @Override
    public void checkDocumentWikiVisible(User loginUser, DocumentWiki documentWiki) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(documentWiki == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(documentWiki.getIsDelete() != null && documentWiki.getIsDelete() == 1, ErrorCode.NOT_FOUND_ERROR);
        wikiSpaceService.requireVisibleSpace(documentWiki.getSpaceId(), loginUser);
    }

    @Override
    public String buildSummary(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        String normalizedContent = content.replaceAll("\\s+", " ").trim();
        return StrUtil.sub(normalizedContent, 0, SUMMARY_LENGTH);
    }

    @Override
    public DocumentWiki getByIdIncludeDeleted(Long id) {
        return baseMapper.selectByIdIncludeDeleted(id);
    }

    @Override
    public Boolean logicalDelete(Long id, Long deleteBy) {
        return baseMapper.logicalDeleteById(id, new Date(), deleteBy) > 0;
    }

    @Override
    public Boolean restore(Long id) {
        DocumentWiki documentWiki = baseMapper.selectByIdIncludeDeleted(id);
        ThrowUtils.throwIf(documentWiki == null, ErrorCode.NOT_FOUND_ERROR);
        return baseMapper.restoreById(id) > 0;
    }

    @Override
    public Boolean permanentDelete(Long id) {
        return baseMapper.physicallyDeleteById(id) > 0;
    }
}

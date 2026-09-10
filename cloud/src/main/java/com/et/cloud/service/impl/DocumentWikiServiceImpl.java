package com.et.cloud.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.et.cloud.rag.WikiDocumentChangedEvent;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiSpaceService;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentWikiServiceImpl extends ServiceImpl<DocumentWikiMapper, DocumentWiki>
        implements DocumentWikiService {

    private static final int MAX_TITLE_LENGTH = 128;

    private static final int MAX_SUMMARY_LENGTH = 512;

    private static final int MAX_CATEGORY_LENGTH = 64;

    // Uploaded HTML pages keep their original markup (inline <style>, class names, scripts),
    // so a single imported document is far larger than a hand-written Markdown note. The
    // database column is longtext, so the limit exists only to stop pathological uploads.
    private static final int MAX_CONTENT_LENGTH = 6000000;

    private static final int SUMMARY_LENGTH = 160;

    private static final int MAX_SOURCE_URL_LENGTH = 1024;

    private static final int MAX_METADATA_LENGTH = 2048;

    private static final List<String> ALLOWED_CONTENT_FORMAT_LIST = Arrays.asList("plain", "markdown", "html");

    private static final List<String> ALLOWED_SOURCE_TYPE_LIST = Arrays.asList("NATIVE", "UPLOAD", "IMPORT", "URL");

    @Resource
    private UserService userService;

    @Resource
    private WikiSpaceService wikiSpaceService;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    // ==================== RAG index hooks ====================
    // All document write entries (manual create, single import, batch file/url
    // import, edit, recycle operations) funnel into these overrides, so the
    // chunk index follows the document lifecycle from one single place.
    // Events are consumed AFTER_COMMIT and asynchronously — indexing problems
    // can never fail a document operation.

    @Override
    public boolean save(DocumentWiki documentWiki) {
        if (documentWiki.getContentVersion() == null) {
            documentWiki.setContentVersion(1);
        }
        if (StrUtil.isBlank(documentWiki.getContentHash()) && documentWiki.getContent() != null) {
            documentWiki.setContentHash(SecureUtil.md5(documentWiki.getContent()));
        }
        boolean saved = super.save(documentWiki);
        if (saved) {
            eventPublisher.publishEvent(WikiDocumentChangedEvent.of(
                    WikiDocumentChangedEvent.ChangeType.DOC_CREATED,
                    documentWiki.getId(), documentWiki.getSpaceId(), documentWiki.getContentVersion()));
        }
        return saved;
    }

    @Override
    public boolean updateById(DocumentWiki documentWiki) {
        DocumentWiki old = documentWiki == null || documentWiki.getId() == null
                ? null : this.getById(documentWiki.getId());
        int newVersion = old == null || old.getContentVersion() == null ? 1 : old.getContentVersion() + 1;
        if (documentWiki != null) {
            documentWiki.setContentVersion(newVersion);
            if (documentWiki.getContent() != null) {
                documentWiki.setContentHash(SecureUtil.md5(documentWiki.getContent()));
            }
        }
        boolean updated = super.updateById(documentWiki);
        if (updated && documentWiki != null) {
            Long spaceId = documentWiki.getSpaceId() != null ? documentWiki.getSpaceId()
                    : (old != null ? old.getSpaceId() : null);
            eventPublisher.publishEvent(WikiDocumentChangedEvent.of(
                    WikiDocumentChangedEvent.ChangeType.DOC_UPDATED,
                    documentWiki.getId(), spaceId, newVersion));
        }
        return updated;
    }

    @Override
    public Boolean logicalDelete(Long id, Long deleteBy) {
        DocumentWiki doc = baseMapper.selectByIdIncludeDeleted(id);
        boolean deleted = baseMapper.logicalDeleteById(id, new Date(), deleteBy) > 0;
        if (deleted && doc != null) {
            eventPublisher.publishEvent(WikiDocumentChangedEvent.of(
                    WikiDocumentChangedEvent.ChangeType.DOC_LOGICAL_DELETED,
                    id, doc.getSpaceId(), doc.getContentVersion()));
        }
        return deleted;
    }

    @Override
    public Boolean restore(Long id) {
        DocumentWiki documentWiki = baseMapper.selectByIdIncludeDeleted(id);
        ThrowUtils.throwIf(documentWiki == null, ErrorCode.NOT_FOUND_ERROR);
        boolean restored = baseMapper.restoreById(id) > 0;
        if (restored) {
            eventPublisher.publishEvent(WikiDocumentChangedEvent.of(
                    WikiDocumentChangedEvent.ChangeType.DOC_RESTORED,
                    id, documentWiki.getSpaceId(), documentWiki.getContentVersion()));
        }
        return restored;
    }

    @Override
    public Boolean permanentDelete(Long id) {
        DocumentWiki doc = baseMapper.selectByIdIncludeDeleted(id);
        boolean deleted = baseMapper.physicallyDeleteById(id) > 0;
        if (deleted && doc != null) {
            eventPublisher.publishEvent(WikiDocumentChangedEvent.of(
                    WikiDocumentChangedEvent.ChangeType.DOC_PERMANENT_DELETED,
                    id, doc.getSpaceId(), null));
        }
        return deleted;
    }

    /**
     * Moves a document (optionally across spaces) with the explicit-SET wrapper
     * (null folderId means space root) and notifies the RAG index.
     */
    @Override
    public boolean moveDocument(Long id, Long targetSpaceId, Long targetFolderId) {
        DocumentWiki doc = this.getById(id);
        ThrowUtils.throwIf(doc == null, ErrorCode.NOT_FOUND_ERROR);
        Long fromSpaceId = doc.getSpaceId();
        // updateById 的默认 NOT_NULL 策略会跳过 null 字段，而「移到空间根目录」正是要写 folderId = null，
        // 必须用 LambdaUpdateWrapper 显式 SET，否则假成功（跨空间移根还会残留旧 folderId 导致文档从树上消失）。
        boolean moved = this.update(new LambdaUpdateWrapper<DocumentWiki>()
                .eq(DocumentWiki::getId, id)
                .set(DocumentWiki::getSpaceId, targetSpaceId)
                .set(DocumentWiki::getFolderId, targetFolderId)
                .set(DocumentWiki::getEditTime, new Date()));
        if (moved) {
            eventPublisher.publishEvent(WikiDocumentChangedEvent.moved(id, fromSpaceId, targetSpaceId));
        }
        return moved;
    }
    // ==================== end RAG index hooks ====================

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
        Long spaceId = documentWikiQueryRequest.getSpaceId();
        Long folderId = documentWikiQueryRequest.getFolderId();
        Long userId = documentWikiQueryRequest.getUserId();
        List<Long> visibleSpaceIds = documentWikiQueryRequest.getVisibleSpaceIds();
        String sortField = documentWikiQueryRequest.getSortField();
        String sortOrder = documentWikiQueryRequest.getSortOrder();
        boolean hasSearchText = StrUtil.isNotBlank(searchText);

        if (hasSearchText) {
            queryWrapper.apply("MATCH (title, content) AGAINST ({0} IN NATURAL LANGUAGE MODE)", searchText);
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
        if (hasSearchText) {
            queryWrapper.orderByDesc("MATCH (title, content) AGAINST ('" + escapeSqlLiteral(searchText)
                    + "' IN NATURAL LANGUAGE MODE)");
        } else if (StringUtils.isNotBlank(sortField)) {
            queryWrapper.orderBy(true, "ascend".equals(sortOrder), sortField);
        } else {
            queryWrapper.orderByDesc("editTime");
        }
        return queryWrapper;
    }

    private String escapeSqlLiteral(String value) {
        return value.replace("\\", "\\\\").replace("'", "''");
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
            documentWikiVisList.add(DocumentWikiVis.objToVis(documentWiki));
        }
        fillUsers(documentWikiVisList);
        documentWikiVisPage.setRecords(documentWikiVisList);
        return documentWikiVisPage;
    }

    private void fillUsers(List<DocumentWikiVis> documentWikiVisList) {
        if (CollUtil.isEmpty(documentWikiVisList) || userService == null) {
            return;
        }
        Set<Long> userIdSet = documentWikiVisList.stream()
                .map(DocumentWikiVis::getUserId)
                .filter(userId -> userId != null && userId > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (CollUtil.isEmpty(userIdSet)) {
            return;
        }
        List<User> users = userService.listByIds(new ArrayList<>(userIdSet));
        if (CollUtil.isEmpty(users)) {
            return;
        }
        Map<Long, User> userIdUserMap = users.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        for (DocumentWikiVis documentWikiVis : documentWikiVisList) {
            User user = userIdUserMap.get(documentWikiVis.getUserId());
            if (user != null) {
                documentWikiVis.setUser(userService.getUserVis(user));
            }
        }
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
        ThrowUtils.throwIf(content.length() > MAX_CONTENT_LENGTH, ErrorCode.PARAMS_ERROR,
                "正文过长，单个文档不能超过 " + MAX_CONTENT_LENGTH + " 字符");
        ThrowUtils.throwIf(StrUtil.isNotBlank(summary) && summary.length() > MAX_SUMMARY_LENGTH, ErrorCode.PARAMS_ERROR, "摘要过长");
        ThrowUtils.throwIf(category != null && category.length() > MAX_CATEGORY_LENGTH, ErrorCode.PARAMS_ERROR, "分类过长");
        ThrowUtils.throwIf(documentWiki.getSpaceId() == null || documentWiki.getSpaceId() <= 0, ErrorCode.PARAMS_ERROR, "空间不能为空");
        String contentFormat = documentWiki.getContentFormat();
        ThrowUtils.throwIf(StrUtil.isNotBlank(contentFormat) && !ALLOWED_CONTENT_FORMAT_LIST.contains(contentFormat),
                ErrorCode.PARAMS_ERROR, "内容格式不合法");
        String sourceType = documentWiki.getSourceType();
        ThrowUtils.throwIf(StrUtil.isNotBlank(sourceType) && !ALLOWED_SOURCE_TYPE_LIST.contains(sourceType),
                ErrorCode.PARAMS_ERROR, "来源类型不合法");
        ThrowUtils.throwIf(StrUtil.isNotBlank(documentWiki.getSourceUrl()) && documentWiki.getSourceUrl().length() > MAX_SOURCE_URL_LENGTH,
                ErrorCode.PARAMS_ERROR, "来源地址过长");
        ThrowUtils.throwIf(StrUtil.isNotBlank(documentWiki.getMetadataJson()) && documentWiki.getMetadataJson().length() > MAX_METADATA_LENGTH,
                ErrorCode.PARAMS_ERROR, "元数据过长");
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
        String normalizedContent = Jsoup.parse(content).text().replaceAll("\\s+", " ").trim();
        return StrUtil.sub(normalizedContent, 0, SUMMARY_LENGTH);
    }

    @Override
    public DocumentWiki getByIdIncludeDeleted(Long id) {
        return baseMapper.selectByIdIncludeDeleted(id);
    }
}

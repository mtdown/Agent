package com.et.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.mapper.WikiSpaceMapper;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiFolder;
import com.et.cloud.service.UserService;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.WikiCacheManager;
import com.et.cloud.service.WikiFolderService;
import com.et.cloud.service.WikiSpaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;

/** Cloud-owned, transactional naming and persistence for Agent Wiki writes. */
@Service
public class AgentWikiCreationService {
    private static final int MAX_NAME_LENGTH = 128;
    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Shanghai");

    private final UserService users;
    private final WikiSpaceService spaces;
    private final WikiSpaceMapper spaceMapper;
    private final WikiFolderService folders;
    private final DocumentWikiService documents;
    private final WikiCacheManager cache;

    public AgentWikiCreationService(UserService users, WikiSpaceService spaces, WikiSpaceMapper spaceMapper,
                                    WikiFolderService folders, DocumentWikiService documents, WikiCacheManager cache) {
        this.users = users;
        this.spaces = spaces;
        this.spaceMapper = spaceMapper;
        this.folders = folders;
        this.documents = documents;
        this.cache = cache;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public FolderResult createFolder(long userId, String rawSpaceId, String taskName) {
        User user = requireUser(userId);
        long spaceId = positiveId(rawSpaceId);
        spaces.requireSpaceAccess(spaceId, user, true);
        lockAndRecheck(spaceId, user);
        String baseName = taskFolderBase(taskName);
        String name = firstFreeName(baseName, candidate -> folders.count(new QueryWrapper<WikiFolder>()
                .eq("spaceId", spaceId).isNull("parentId").eq("isDelete", 0).eq("name", candidate)) > 0);
        WikiFolder folder = new WikiFolder();
        folder.setSpaceId(spaceId);
        folder.setName(name);
        folder.setIsDelete(0);
        folder.setEditTime(new Date());
        if (!folders.save(folder) || folder.getId() == null)
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务文件夹保存失败");
        clearSpaceAfterCommit(spaceId);
        return new FolderResult(spaceId, folder.getId(), name);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public DocumentResult createDocument(long userId, String rawSpaceId, String rawFolderId,
                                         String requestedTitle, String content) {
        User user = requireUser(userId);
        long spaceId = positiveId(rawSpaceId);
        long folderId = positiveId(rawFolderId);
        spaces.requireSpaceAccess(spaceId, user, true);
        lockAndRecheck(spaceId, user);
        WikiFolder folder = folders.requireFolderAccess(folderId, spaceId, user, true);
        String baseTitle = documentBaseTitle(requestedTitle, content);
        String title = firstFreeName(baseTitle, candidate -> documents.count(new QueryWrapper<DocumentWiki>()
                .eq("spaceId", spaceId).eq("folderId", folderId).eq("isDelete", 0)
                .eq("title", candidate)) > 0);
        DocumentWiki document = new DocumentWiki();
        document.setSpaceId(spaceId);
        document.setFolderId(folderId);
        document.setTitle(title);
        document.setContent(content);
        document.setSummary(documents.buildSummary(content));
        document.setTags("[]");
        document.setContentFormat("markdown");
        document.setSourceType("NATIVE");
        document.setUserId(userId);
        document.setViewCount(0L);
        document.setIsDelete(0);
        document.setEditTime(new Date());
        documents.validDocumentWiki(document);
        if (!documents.save(document) || document.getId() == null)
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文档保存失败");
        clearSpaceAfterCommit(spaceId);
        return new DocumentResult(spaceId, folderId, document.getId(), title, folder.getName());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public DocumentResult createLegacyDocument(long userId, String spaceId, String title, String content) {
        FolderResult folder = createFolder(userId, spaceId, title);
        return createDocument(userId, spaceId, folder.folderId, title, content);
    }

    private User requireUser(long userId) {
        User user = users.getById(userId);
        if (user == null) throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        return user;
    }

    private void lockAndRecheck(long spaceId, User user) {
        if (!Objects.equals(spaceMapper.lockActiveById(spaceId), spaceId))
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间不存在或已删除");
        spaces.requireSpaceAccess(spaceId, user, true);
    }

    private static long positiveId(String raw) {
        if (raw == null || !raw.matches("[1-9][0-9]{0,18}"))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 ID 不合法");
        try { return Long.parseLong(raw); }
        catch (NumberFormatException e) { throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 ID 不合法"); }
    }

    private static String taskFolderBase(String taskName) {
        String normalized = taskName == null ? "" : taskName.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (normalized.isEmpty()) throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务内容不能为空");
        String date = LocalDate.now(USER_ZONE).toString();
        return truncate(normalized, MAX_NAME_LENGTH - date.length() - 1) + " " + date;
    }

    private static String documentBaseTitle(String requestedTitle, String content) {
        String source = requestedTitle == null ? "" : requestedTitle.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (source.isEmpty() && content != null) {
            for (String line : content.split("\\R")) {
                String candidate = line.replaceFirst("^\\s*#{1,6}\\s*", "")
                        .replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
                if (!candidate.isEmpty()) { source = candidate; break; }
            }
        }
        if (source.isEmpty()) source = "未命名文档";
        return truncate(source, MAX_NAME_LENGTH);
    }

    private static String firstFreeName(String base, java.util.function.Predicate<String> exists) {
        for (int index = 0; index < Integer.MAX_VALUE; index++) {
            String suffix = index == 0 ? "" : "（" + index + "）";
            String candidate = truncate(base, MAX_NAME_LENGTH - suffix.length()) + suffix;
            if (!exists.test(candidate)) return candidate;
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称已用尽");
    }

    private static String truncate(String value, int maxLength) {
        if (maxLength < 1) throw new BusinessException(ErrorCode.PARAMS_ERROR, "名称过长");
        if (value.length() <= maxLength) return value;
        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1)) && Character.isLowSurrogate(value.charAt(end)))
            end--;
        return value.substring(0, end).trim();
    }

    private void clearSpaceAfterCommit(long spaceId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache.clearSpace(spaceId); }
        });
    }

    public static final class FolderResult {
        public final String spaceId;
        public final String folderId;
        public final String name;

        FolderResult(long spaceId, long folderId, String name) {
            this.spaceId = Long.toString(spaceId);
            this.folderId = Long.toString(folderId);
            this.name = name;
        }
    }

    public static final class DocumentResult {
        public final String spaceId;
        public final String folderId;
        public final String documentId;
        public final String title;
        public final String folderName;

        DocumentResult(long spaceId, long folderId, long documentId, String title, String folderName) {
            this.spaceId = Long.toString(spaceId);
            this.folderId = Long.toString(folderId);
            this.documentId = Long.toString(documentId);
            this.title = title;
            this.folderName = folderName;
        }
    }
}

package com.et.cloud.service.impl;

import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.model.dto.ImportedWikiDocument;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiFolder;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.WikiCacheManager;
import com.et.cloud.service.WikiFolderService;
import com.et.cloud.service.WikiSpaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Agent-only results: a failed commit is never treated as a failed write. */
@Service
public class AgentUrlImportService {
    private static final Logger LOG = LoggerFactory.getLogger(AgentUrlImportService.class);
    private final WikiBatchImportServiceImpl core;
    private final WikiMutationLock lock;
    private final WikiSpaceService spaces;
    private final WikiFolderService folders;
    private final DocumentWikiService documents;
    private final WikiCacheManager cache;
    private final TransactionTemplate transaction;
    @Value("${wiki.batch-import.max-items:20}") private int maxItems = 20;

    public AgentUrlImportService(WikiBatchImportServiceImpl core, WikiMutationLock lock, WikiSpaceService spaces,
            WikiFolderService folders, DocumentWikiService documents, WikiCacheManager cache,
            PlatformTransactionManager manager) {
        this.core=core; this.lock=lock; this.spaces=spaces; this.folders=folders; this.documents=documents; this.cache=cache;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public static final class TargetSnapshot {
        public final String spaceLabel;
        public final List<String> path;
        public TargetSnapshot(String spaceLabel, List<String> path) {
            this.spaceLabel=spaceLabel; this.path=List.copyOf(path);
        }
    }
    public Map<String,Object> importUrls(long userId, long spaceId, Long folderId, List<String> urls) {
        return importUrls(userId,spaceId,folderId,urls,null);
    }
    public Map<String,Object> importUrls(long userId, long spaceId, Long folderId, List<String> urls, TargetSnapshot snapshot) {
        if (urls == null || urls.isEmpty() || urls.size() > maxItems)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的网页数量");
        for (String url : urls)
            if (url == null || url.isBlank() || url.length() > 2048 || url.matches(".*[\\r\\n,].*"))
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的网页地址");
        List<Map<String,Object>> results=new ArrayList<>();
        boolean unknown=false;
        for (String url: urls) {
            Map<String,Object> item=new LinkedHashMap<>(); item.put("input",url);
            results.add(item);
            if (unknown) { item.put("status","not_executed"); continue; }
            try {
                transaction.execute(status -> {
                    User user=lock.acquire(spaceId,userId);
                    spaces.requireEditableSpace(spaceId,user);
                    if (folderId!=null) folders.requireFolderAccess(folderId,spaceId,user,true);
                    return null;
                });
            } catch (RuntimeException invalidTarget) {
                item.put("status","failed"); item.put("message","目标不可用或无写入权限"); continue;
            }
            final ImportedWikiDocument imported;
            try { imported=core.prepareUrl(url); }
            catch (Exception failure) { item.put("status","failed"); item.put("message","网页获取或正文提取失败"); continue; }
            final boolean[] attempted={false};
            try {
                Map<String,Object> saved=transaction.execute(status -> {
                    User user=lock.acquire(spaceId,userId);
                    WikiSpace space=spaces.requireEditableSpace(spaceId,user);
                    WikiFolder folder=folderId == null ? null : folders.requireFolderAccess(folderId,spaceId,user,true);
                    if (snapshot != null) verifyTargetSnapshot(space, folder, user, snapshot);
                    DocumentWiki doc=core.buildDocument(imported,space,folderId,user);
                    attempted[0]=true;
                    if (!documents.save(doc) || doc.getId()==null) throw new IllegalStateException("Document persistence was not confirmed");
                    Map<String,Object> result=new LinkedHashMap<>();
                    result.put("documentId",doc.getId().toString()); result.put("spaceId",Long.toString(spaceId));
                    if (folderId!=null) result.put("folderId",Long.toString(folderId));
                    result.put("folderName",folder==null ? "" : folder.getName());
                    return result;
                });
                if (saved==null) throw new IllegalStateException("Document transaction returned no result");
                item.putAll(saved); item.put("status","success"); item.put("title",imported.getTitle());
                item.put("sourceUrl",imported.getSourceUrl());
                try { cache.clearSpace(spaceId); }
                catch (RuntimeException first) {
                    LOG.warn("Wiki cache clear failed after import: {}",first.getClass().getSimpleName());
                    try { cache.clearSpace(spaceId); }
                    catch (RuntimeException second) {
                        LOG.warn("Wiki cache clear retry failed after import: {}",second.getClass().getSimpleName());
                        item.put("warning","文档已保存，缓存刷新失败");
                    }
                }
            } catch (RuntimeException failure) {
                if (failure instanceof TargetChangedException) { item.put("status","failed"); item.put("message","目标名称或目录路径已变化，请重新确认"); }
                else if (attempted[0]) { item.put("status","unknown"); unknown=true; }
                else { item.put("status","failed"); item.put("message","目标不可用或无写入权限"); }
            }
        }
        Map<String,Object> result=new LinkedHashMap<>(); result.put("items",results);
        if (unknown) result.put("status","unknown");
        return result;
    }
    private static final class TargetChangedException extends IllegalStateException { }
    private void verifyTargetSnapshot(WikiSpace space, WikiFolder folder, User user, TargetSnapshot snapshot) {
        String type = Integer.valueOf(WikiSpaceService.TYPE_PERSONAL).equals(space.getType()) ? "personal" :
                Integer.valueOf(WikiSpaceService.TYPE_TEAM).equals(space.getType()) ? "team" :
                Integer.valueOf(WikiSpaceService.TYPE_PUBLIC).equals(space.getType()) ? "public" : null;
        if (type == null) throw new TargetChangedException();
        String label=com.et.cloud.controller.AgentWikiChoiceService.displayLabel(type,space.getName());
        if (!label.equals(snapshot.spaceLabel)) throw new TargetChangedException();
        List<String> path=new ArrayList<>(); java.util.Set<Long> seen=new java.util.HashSet<>();
        WikiFolder current=folder;
        while(current!=null) {
            if(!seen.add(current.getId()) || path.size()>=100) throw new IllegalStateException("Invalid Wiki folder path");
            path.add(current.getId()+":"+current.getName());
            current=current.getParentId()==null ? null : folders.requireFolderAccess(current.getParentId(),space.getId(),user,true);
        }
        java.util.Collections.reverse(path);
        if(!path.equals(snapshot.path)) throw new TargetChangedException();
    }
}

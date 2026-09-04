package com.et.cloud.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.mapper.DocumentWikiMapper;
import com.et.cloud.mapper.UserMapper;
import com.et.cloud.mapper.WikiFolderMapper;
import com.et.cloud.mapper.WikiSpaceMapper;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiFolder;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.model.vis.DocumentWikiVis;
import com.et.cloud.model.vis.UserVis;
import com.et.cloud.model.vis.WikiFolderVis;
import com.et.cloud.service.WikiCacheManager;
import com.et.cloud.service.WikiFolderService;
import com.et.cloud.service.WikiSpaceService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WikiFolderServiceImpl extends ServiceImpl<WikiFolderMapper, WikiFolder> implements WikiFolderService {

    @Resource
    private WikiSpaceService wikiSpaceService;

    @Resource
    private WikiSpaceMapper wikiSpaceMapper;

    @Resource
    private DocumentWikiMapper documentWikiMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private WikiCacheManager wikiCacheManager;

    @Override
    public WikiFolder requireVisibleFolder(Long folderId, Long expectedSpaceId, User loginUser) {
        ThrowUtils.throwIf(folderId == null || folderId <= 0, ErrorCode.PARAMS_ERROR);
        WikiFolder wikiFolder = this.getById(folderId);
        ThrowUtils.throwIf(wikiFolder == null || Objects.equals(wikiFolder.getIsDelete(), 1), ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(expectedSpaceId != null && !Objects.equals(expectedSpaceId, wikiFolder.getSpaceId()), ErrorCode.PARAMS_ERROR);
        WikiSpace wikiSpace = wikiSpaceService.requireVisibleSpace(wikiFolder.getSpaceId(), loginUser);
        ThrowUtils.throwIf(wikiSpace == null, ErrorCode.NO_AUTH_ERROR);
        return wikiFolder;
    }

    @Override
    public Long createFolder(Long spaceId, Long parentId, String name, User loginUser) {
        wikiSpaceService.requireVisibleSpace(spaceId, loginUser);
        ThrowUtils.throwIf(StrUtil.isBlank(name), ErrorCode.PARAMS_ERROR, "文件夹名称不能为空");
        if (parentId != null) {
            requireVisibleFolder(parentId, spaceId, loginUser);
        }
        WikiFolder wikiFolder = new WikiFolder();
        wikiFolder.setSpaceId(spaceId);
        wikiFolder.setParentId(parentId);
        wikiFolder.setName(name);
        wikiFolder.setIsDelete(0);
        wikiFolder.setEditTime(new Date());
        this.save(wikiFolder);
        wikiCacheManager.clearSpace(spaceId);
        return wikiFolder.getId();
    }

    @Override
    public Boolean renameFolder(Long folderId, String name, User loginUser) {
        WikiFolder oldFolder = requireVisibleFolder(folderId, null, loginUser);
        ThrowUtils.throwIf(StrUtil.isBlank(name), ErrorCode.PARAMS_ERROR, "文件夹名称不能为空");
        oldFolder.setName(name);
        oldFolder.setEditTime(new Date());
        boolean result = this.updateById(oldFolder);
        wikiCacheManager.clearSpace(oldFolder.getSpaceId());
        return result;
    }

    @Override
    public Boolean moveFolder(Long folderId, Long parentId, User loginUser) {
        WikiFolder oldFolder = requireVisibleFolder(folderId, null, loginUser);
        ThrowUtils.throwIf(Objects.equals(folderId, parentId), ErrorCode.PARAMS_ERROR, "不能移动到自身");
        if (parentId != null) {
            WikiFolder parent = requireVisibleFolder(parentId, oldFolder.getSpaceId(), loginUser);
            List<Long> subtreeIds = collectFolderSubtreeIds(oldFolder.getSpaceId(), oldFolder.getId());
            ThrowUtils.throwIf(subtreeIds.contains(parent.getId()), ErrorCode.PARAMS_ERROR, "不能移动到子文件夹");
        }
        oldFolder.setParentId(parentId);
        oldFolder.setEditTime(new Date());
        boolean result = this.updateById(oldFolder);
        wikiCacheManager.clearSpace(oldFolder.getSpaceId());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteFolderTree(Long folderId, User loginUser) {
        WikiFolder oldFolder = requireVisibleFolder(folderId, null, loginUser);
        List<Long> subtreeIds = collectFolderSubtreeIds(oldFolder.getSpaceId(), folderId);
        String ids = joinIds(subtreeIds);
        Date now = new Date();
        baseMapper.logicalDeleteByIds(ids, now, loginUser.getId());
        documentWikiMapper.logicalDeleteByFolderIds(ids, now, loginUser.getId());
        wikiCacheManager.clearSpace(oldFolder.getSpaceId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean restoreFolderTree(Long folderId, User loginUser) {
        WikiFolder folder = baseMapper.selectByIdIncludeDeleted(folderId);
        ThrowUtils.throwIf(folder == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!Objects.equals(folder.getIsDelete(), 1), ErrorCode.PARAMS_ERROR, "该文件夹不在回收站中");
        WikiSpace space = wikiSpaceMapper.selectByIdIncludeDeleted(folder.getSpaceId());
        ThrowUtils.throwIf(!wikiSpaceService.checkSpaceVisible(space, loginUser), ErrorCode.NO_AUTH_ERROR);
        if (folder.getParentId() != null && baseMapper.selectByIdIncludeDeleted(folder.getParentId()) == null) {
            baseMapper.moveToRoot(folder.getId());
        }
        List<Long> subtreeIds = collectFolderSubtreeIds(folder.getSpaceId(), folderId);
        baseMapper.restoreByIds(joinIds(subtreeIds));
        documentWikiMapper.restoreByFolderIds(joinIds(subtreeIds));
        wikiCacheManager.clearSpace(folder.getSpaceId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean permanentDeleteFolderTree(Long folderId, User loginUser) {
        WikiFolder folder = baseMapper.selectByIdIncludeDeleted(folderId);
        ThrowUtils.throwIf(folder == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!Objects.equals(folder.getIsDelete(), 1), ErrorCode.PARAMS_ERROR, "该文件夹不在回收站中");
        WikiSpace space = wikiSpaceMapper.selectByIdIncludeDeleted(folder.getSpaceId());
        ThrowUtils.throwIf(!wikiSpaceService.checkSpaceVisible(space, loginUser), ErrorCode.NO_AUTH_ERROR);
        List<Long> subtreeIds = collectFolderSubtreeIds(folder.getSpaceId(), folderId);
        String ids = joinIds(subtreeIds);
        documentWikiMapper.physicallyDeleteByFolderIds(ids);
        baseMapper.physicallyDeleteByIds(ids);
        wikiCacheManager.clearSpace(folder.getSpaceId());
        return true;
    }

    @Override
    public List<WikiFolderVis> listFolderTree(Long spaceId, User loginUser) {
        wikiSpaceService.requireVisibleSpace(spaceId, loginUser);
        List<WikiFolder> folders = this.lambdaQuery()
                .eq(WikiFolder::getSpaceId, spaceId)
                .eq(WikiFolder::getIsDelete, 0)
                .orderByDesc(WikiFolder::getEditTime)
                .list();
        List<DocumentWiki> documents = documentWikiMapper.selectList(new QueryWrapper<DocumentWiki>()
                .eq("spaceId", spaceId)
                .eq("isDelete", 0)
                .orderByDesc("editTime"));
        Map<Long, WikiFolderVis> folderMap = new HashMap<>();
        List<WikiFolderVis> roots = new ArrayList<>();
        for (WikiFolder folder : folders) {
            folderMap.put(folder.getId(), WikiFolderVis.objToVis(folder));
        }
        for (WikiFolderVis folder : folderMap.values()) {
            if (folder.getParentId() != null && folderMap.containsKey(folder.getParentId())) {
                folderMap.get(folder.getParentId()).getChildren().add(folder);
            } else {
                roots.add(folder);
            }
        }
        for (DocumentWiki documentWiki : documents) {
            DocumentWikiVis documentWikiVis = DocumentWikiVis.objToVis(documentWiki);
            fillUser(documentWikiVis, documentWiki.getUserId());
            if (documentWiki.getFolderId() != null && folderMap.containsKey(documentWiki.getFolderId())) {
                folderMap.get(documentWiki.getFolderId()).getDocuments().add(documentWikiVis);
            }
        }
        return roots;
    }

    @Override
    public List<Long> collectFolderSubtreeIds(Long spaceId, Long folderId) {
        List<WikiFolder> allFolders = baseMapper.selectAllBySpaceId(spaceId);
        Set<Long> visited = new HashSet<>();
        collect(folderId, allFolders, visited);
        return new ArrayList<>(visited);
    }

    private void collect(Long folderId, List<WikiFolder> allFolders, Set<Long> visited) {
        if (folderId == null || visited.contains(folderId)) {
            return;
        }
        visited.add(folderId);
        for (WikiFolder folder : allFolders) {
            if (Objects.equals(folderId, folder.getParentId())) {
                collect(folder.getId(), allFolders, visited);
            }
        }
    }

    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private void fillUser(DocumentWikiVis documentWikiVis, Long userId) {
        if (documentWikiVis == null || userId == null) {
            return;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        UserVis userVis = new UserVis();
        BeanUtils.copyProperties(user, userVis);
        documentWikiVis.setUser(userVis);
    }
}

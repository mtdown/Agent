package com.et.cloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiFolder;
import com.et.cloud.model.vis.WikiFolderVis;

import java.util.List;

public interface WikiFolderService extends IService<WikiFolder> {

    WikiFolder requireVisibleFolder(Long folderId, Long expectedSpaceId, User loginUser);

    Long createFolder(Long spaceId, Long parentId, String name, User loginUser);

    Boolean renameFolder(Long folderId, String name, User loginUser);

    Boolean moveFolder(Long folderId, Long parentId, User loginUser);

    Boolean deleteFolderTree(Long folderId, User loginUser);

    Boolean restoreFolderTree(Long folderId, User loginUser);

    Boolean permanentDeleteFolderTree(Long folderId, User loginUser);

    List<WikiFolderVis> listFolderTree(Long spaceId, User loginUser);

    List<Long> collectFolderSubtreeIds(Long spaceId, Long folderId);
}

package com.et.cloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.model.vis.WikiSpaceUserVis;
import com.et.cloud.model.vis.WikiSpaceVis;

import java.util.List;

public interface WikiSpaceService extends IService<WikiSpace> {

    int TYPE_PERSONAL = 0;
    int TYPE_TEAM = 1;
    int TYPE_PUBLIC = 2;

    String ROLE_EDITOR = "editor";

    WikiSpace ensurePublicSpace();

    WikiSpace ensurePersonalSpaceForUser(Long userId);

    boolean checkSpaceVisible(WikiSpace wikiSpace, User loginUser);

    WikiSpace requireVisibleSpace(Long spaceId, User loginUser);

    List<WikiSpaceVis> listVisibleSpaceVis(User loginUser);

    List<Long> listVisibleSpaceIds(User loginUser);

    Long createTeamSpace(String name, User loginUser);

    Long addTeamMember(Long spaceId, Long userId, String spaceRole, User loginUser);

    Boolean removeTeamMember(Long spaceId, Long userId, User loginUser);

    Boolean exitTeamSpace(Long spaceId, User loginUser);

    List<WikiSpaceUserVis> listTeamMembers(Long spaceId, User loginUser);

    List<WikiSpaceVis> listManageTeamSpaces(User loginUser);

    Boolean deleteTeamSpace(Long spaceId, Boolean confirm, User loginUser);

    Boolean restoreTeamSpace(Long spaceId, User loginUser);

    Boolean permanentDeleteTeamSpace(Long spaceId, Boolean confirm, User loginUser);
}

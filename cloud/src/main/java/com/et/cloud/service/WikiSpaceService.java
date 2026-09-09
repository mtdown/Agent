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

    /**
     * 个人空间的默认名称。名字由系统持有，不允许任何角色修改，
     * 存量订正见 sql/normalize_wiki_space_personal_name.sql。
     */
    String PERSONAL_SPACE_DEFAULT_NAME = "个人区";

    WikiSpace ensurePublicSpace();

    WikiSpace ensurePersonalSpaceForUser(Long userId);

    boolean checkSpaceVisible(WikiSpace wikiSpace, User loginUser);

    WikiSpace requireVisibleSpace(Long spaceId, User loginUser);

    boolean checkSpaceEditable(WikiSpace wikiSpace, User loginUser);

    /**
     * 是否可重命名。与 checkSpaceEditable 的口径不同：只放行团队空间，且只认 admin 成员。
     */
    boolean checkSpaceRenamable(WikiSpace wikiSpace, User loginUser);

    WikiSpace requireEditableSpace(Long spaceId, User loginUser);

    List<WikiSpaceVis> listVisibleSpaceVis(User loginUser);

    List<Long> listVisibleSpaceIds(User loginUser);

    Long createTeamSpace(String name, User loginUser);

    Long addTeamMember(Long spaceId, Long userId, String spaceRole, User loginUser);

    Boolean removeTeamMember(Long spaceId, Long userId, User loginUser);

    Boolean exitTeamSpace(Long spaceId, User loginUser);

    List<WikiSpaceUserVis> listTeamMembers(Long spaceId, User loginUser);

    List<WikiSpaceVis> listManageTeamSpaces(User loginUser);

    Boolean renameSpace(Long spaceId, String name, User loginUser);

    Boolean deleteTeamSpace(Long spaceId, Boolean confirm, User loginUser);

    Boolean restoreTeamSpace(Long spaceId, User loginUser);

    Boolean permanentDeleteTeamSpace(Long spaceId, Boolean confirm, User loginUser);
}

package com.et.cloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.et.cloud.enums.SpaceRoleEnum;
import com.et.cloud.mapper.WikiSpaceUserMapper;
import com.et.cloud.model.constant.UserConstant;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.impl.WikiSpaceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WikiSpaceServiceImplTest {

    private WikiSpaceUserMapper wikiSpaceUserMapper;
    private WikiSpaceServiceImpl wikiSpaceService;

    @BeforeEach
    void setUp() {
        wikiSpaceUserMapper = mock(WikiSpaceUserMapper.class);
        wikiSpaceService = new WikiSpaceServiceImpl();
        ReflectionTestUtils.setField(wikiSpaceService, "wikiSpaceUserMapper", wikiSpaceUserMapper);
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        return user;
    }

    private WikiSpace space(Integer type, Long ownerUserId) {
        WikiSpace wikiSpace = new WikiSpace();
        wikiSpace.setId(1L);
        wikiSpace.setType(type);
        wikiSpace.setOwnerUserId(ownerUserId);
        wikiSpace.setIsDelete(0);
        return wikiSpace;
    }

    @Test
    void publicSpaceVisibleToAnyLoggedInUser() {
        WikiSpace publicSpace = space(WikiSpaceService.TYPE_PUBLIC, null);
        User ordinaryUser = user(100L, UserConstant.DEFAULT_ROLE);

        assertTrue(wikiSpaceService.checkSpaceVisible(publicSpace, ordinaryUser));
    }

    @Test
    void personalSpaceVisibleOnlyToItsOwner() {
        WikiSpace personalSpace = space(WikiSpaceService.TYPE_PERSONAL, 100L);

        assertTrue(wikiSpaceService.checkSpaceVisible(personalSpace, user(100L, UserConstant.DEFAULT_ROLE)));
        assertFalse(wikiSpaceService.checkSpaceVisible(personalSpace, user(200L, UserConstant.DEFAULT_ROLE)));
    }

    @Test
    void teamSpaceVisibleOnlyToMember() {
        WikiSpace teamSpace = space(WikiSpaceService.TYPE_TEAM, null);
        User member = user(100L, UserConstant.DEFAULT_ROLE);
        User nonMember = user(200L, UserConstant.DEFAULT_ROLE);

        when(wikiSpaceUserMapper.selectCount(any())).thenReturn(1L);
        assertTrue(wikiSpaceService.checkSpaceVisible(teamSpace, member));

        when(wikiSpaceUserMapper.selectCount(any())).thenReturn(0L);
        assertFalse(wikiSpaceService.checkSpaceVisible(teamSpace, nonMember));
    }

    @Test
    void platformAdminCanSeeAnySpace() {
        User admin = user(1L, UserConstant.ADMIN_ROLE);

        assertTrue(wikiSpaceService.checkSpaceVisible(space(WikiSpaceService.TYPE_PUBLIC, null), admin));
        assertTrue(wikiSpaceService.checkSpaceVisible(space(WikiSpaceService.TYPE_PERSONAL, 100L), admin));
        assertTrue(wikiSpaceService.checkSpaceVisible(space(WikiSpaceService.TYPE_TEAM, null), admin));
    }

    @Test
    void deletedSpaceIsNotVisible() {
        WikiSpace deletedSpace = space(WikiSpaceService.TYPE_PUBLIC, null);
        deletedSpace.setIsDelete(1);

        assertFalse(wikiSpaceService.checkSpaceVisible(deletedSpace, user(100L, UserConstant.DEFAULT_ROLE)));
    }

    @Test
    void personalSpaceEditableOnlyByOwner() {
        WikiSpace personalSpace = space(WikiSpaceService.TYPE_PERSONAL, 100L);

        assertTrue(wikiSpaceService.checkSpaceEditable(personalSpace, user(100L, UserConstant.DEFAULT_ROLE)));
        assertFalse(wikiSpaceService.checkSpaceEditable(personalSpace, user(200L, UserConstant.DEFAULT_ROLE)));
    }

    @Test
    void teamSpaceEditableOnlyByAdminOrEditorMember() {
        WikiSpace teamSpace = space(WikiSpaceService.TYPE_TEAM, null);
        User member = user(100L, UserConstant.DEFAULT_ROLE);

        when(wikiSpaceUserMapper.selectCount(any())).thenReturn(1L);
        assertTrue(wikiSpaceService.checkSpaceEditable(teamSpace, member));

        when(wikiSpaceUserMapper.selectCount(any())).thenReturn(0L);
        assertFalse(wikiSpaceService.checkSpaceEditable(teamSpace, member));
    }

    @Test
    void publicSpaceIsNotEditableByOrdinaryUser() {
        WikiSpace publicSpace = space(WikiSpaceService.TYPE_PUBLIC, null);

        assertFalse(wikiSpaceService.checkSpaceEditable(publicSpace, user(100L, UserConstant.DEFAULT_ROLE)));
    }

    @Test
    void platformAdminCanEditAnySpace() {
        User admin = user(1L, UserConstant.ADMIN_ROLE);

        assertTrue(wikiSpaceService.checkSpaceEditable(space(WikiSpaceService.TYPE_PUBLIC, null), admin));
        assertTrue(wikiSpaceService.checkSpaceEditable(space(WikiSpaceService.TYPE_PERSONAL, 100L), admin));
        assertTrue(wikiSpaceService.checkSpaceEditable(space(WikiSpaceService.TYPE_TEAM, null), admin));
    }

    @Test
    void platformAdminCanRenameTeamSpace() {
        User admin = user(1L, UserConstant.ADMIN_ROLE);

        assertTrue(wikiSpaceService.checkSpaceRenamable(space(WikiSpaceService.TYPE_TEAM, null), admin));
    }

    @Test
    void teamSpaceRenamableByItsAdminMember() {
        WikiSpace teamSpace = space(WikiSpaceService.TYPE_TEAM, null);
        User member = user(100L, UserConstant.DEFAULT_ROLE);

        when(wikiSpaceUserMapper.selectCount(any())).thenReturn(1L);
        assertTrue(wikiSpaceService.checkSpaceRenamable(teamSpace, member));
    }

    @Test
    void teamSpaceNotRenamableByMemberWithoutAdminRole() {
        WikiSpace teamSpace = space(WikiSpaceService.TYPE_TEAM, null);
        User editor = user(100L, UserConstant.DEFAULT_ROLE);

        // editor / viewer 成员在库里匹配不到 admin 角色的成员关系，因此被拒
        when(wikiSpaceUserMapper.selectCount(any())).thenReturn(0L);
        assertFalse(wikiSpaceService.checkSpaceRenamable(teamSpace, editor));
    }

    @Test
    void personalSpaceNotRenamableByItsOwner() {
        WikiSpace personalSpace = space(WikiSpaceService.TYPE_PERSONAL, 100L);

        assertFalse(wikiSpaceService.checkSpaceRenamable(personalSpace, user(100L, UserConstant.DEFAULT_ROLE)));
    }

    @Test
    void personalSpaceNotRenamableByPlatformAdmin() {
        WikiSpace personalSpace = space(WikiSpaceService.TYPE_PERSONAL, 100L);

        assertFalse(wikiSpaceService.checkSpaceRenamable(personalSpace, user(1L, UserConstant.ADMIN_ROLE)));
    }

    @Test
    void publicSpaceNotRenamableByPlatformAdmin() {
        WikiSpace publicSpace = space(WikiSpaceService.TYPE_PUBLIC, null);

        assertFalse(wikiSpaceService.checkSpaceRenamable(publicSpace, user(1L, UserConstant.ADMIN_ROLE)));
    }

    @Test
    void deletedTeamSpaceNotRenamable() {
        WikiSpace deletedTeamSpace = space(WikiSpaceService.TYPE_TEAM, null);
        deletedTeamSpace.setIsDelete(1);
        User admin = user(1L, UserConstant.ADMIN_ROLE);

        assertFalse(wikiSpaceService.checkSpaceRenamable(deletedTeamSpace, admin));
    }

    // 关键回归点：重命名不能复用 checkSpaceEditable —— 后者对 editor 也放行。
    @Test
    void teamSpaceRenameOnlyMatchesAdminRole() {
        WikiSpace teamSpace = space(WikiSpaceService.TYPE_TEAM, null);
        User member = user(100L, UserConstant.DEFAULT_ROLE);
        AtomicReference<Map<String, Object>> boundParams = new AtomicReference<>();
        when(wikiSpaceUserMapper.selectCount(any())).thenAnswer(invocation -> {
            QueryWrapper<?> wrapper = invocation.getArgument(0);
            // 触发 SQL 渲染，否则 paramNameValuePairs 仍是懒加载的空表
            wrapper.getTargetSql();
            boundParams.set(wrapper.getParamNameValuePairs());
            return 1L;
        });

        wikiSpaceService.checkSpaceRenamable(teamSpace, member);

        Map<String, Object> params = boundParams.get();
        assertTrue(params.containsValue(SpaceRoleEnum.ADMIN.getValue()), "重命名应按 admin 角色过滤成员");
        assertFalse(params.containsValue(SpaceRoleEnum.EDITOR.getValue()), "重命名不应放行 editor 角色");
    }
}

package com.et.cloud.service;

import com.et.cloud.mapper.WikiSpaceUserMapper;
import com.et.cloud.model.constant.UserConstant;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.impl.WikiSpaceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
}

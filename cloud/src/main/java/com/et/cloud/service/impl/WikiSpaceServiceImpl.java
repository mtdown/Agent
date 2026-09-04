package com.et.cloud.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.mapper.DocumentWikiMapper;
import com.et.cloud.mapper.UserMapper;
import com.et.cloud.mapper.WikiFolderMapper;
import com.et.cloud.mapper.WikiSpaceMapper;
import com.et.cloud.mapper.WikiSpaceUserMapper;
import com.et.cloud.model.constant.UserConstant;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.model.entity.WikiSpaceUser;
import com.et.cloud.model.vis.UserVis;
import com.et.cloud.model.vis.WikiSpaceUserVis;
import com.et.cloud.model.vis.WikiSpaceVis;
import com.et.cloud.service.WikiCacheManager;
import com.et.cloud.service.WikiSpaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WikiSpaceServiceImpl extends ServiceImpl<WikiSpaceMapper, WikiSpace> implements WikiSpaceService {

    @Resource
    private WikiSpaceUserMapper wikiSpaceUserMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private WikiFolderMapper wikiFolderMapper;

    @Resource
    private DocumentWikiMapper documentWikiMapper;

    @Resource
    private WikiCacheManager wikiCacheManager;

    @Override
    public WikiSpace ensurePublicSpace() {
        WikiSpace wikiSpace = this.lambdaQuery()
                .eq(WikiSpace::getType, TYPE_PUBLIC)
                .eq(WikiSpace::getIsDelete, 0)
                .one();
        if (wikiSpace != null) {
            return wikiSpace;
        }
        wikiSpace = new WikiSpace();
        wikiSpace.setType(TYPE_PUBLIC);
        wikiSpace.setName("公开文档");
        wikiSpace.setIsDelete(0);
        this.save(wikiSpace);
        return wikiSpace;
    }

    @Override
    public WikiSpace ensurePersonalSpaceForUser(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR);
        WikiSpace wikiSpace = this.lambdaQuery()
                .eq(WikiSpace::getType, TYPE_PERSONAL)
                .eq(WikiSpace::getOwnerUserId, userId)
                .eq(WikiSpace::getIsDelete, 0)
                .one();
        if (wikiSpace != null) {
            return wikiSpace;
        }
        wikiSpace = new WikiSpace();
        wikiSpace.setType(TYPE_PERSONAL);
        wikiSpace.setName("个人文档");
        wikiSpace.setOwnerUserId(userId);
        wikiSpace.setIsDelete(0);
        this.save(wikiSpace);
        return wikiSpace;
    }

    @Override
    public boolean checkSpaceVisible(WikiSpace wikiSpace, User loginUser) {
        if (wikiSpace == null || loginUser == null || Objects.equals(wikiSpace.getIsDelete(), 1)) {
            return false;
        }
        if (isAdmin(loginUser)) {
            return true;
        }
        if (Objects.equals(wikiSpace.getType(), TYPE_PUBLIC)) {
            return true;
        }
        if (Objects.equals(wikiSpace.getType(), TYPE_PERSONAL)) {
            return Objects.equals(wikiSpace.getOwnerUserId(), loginUser.getId());
        }
        if (Objects.equals(wikiSpace.getType(), TYPE_TEAM)) {
            return wikiSpaceUserMapper.selectCount(new QueryWrapper<WikiSpaceUser>()
                    .eq("spaceId", wikiSpace.getId())
                    .eq("userId", loginUser.getId())
                    .eq("isDelete", 0)) > 0;
        }
        return false;
    }

    @Override
    public WikiSpace requireVisibleSpace(Long spaceId, User loginUser) {
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
        WikiSpace wikiSpace = this.getById(spaceId);
        ThrowUtils.throwIf(wikiSpace == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!checkSpaceVisible(wikiSpace, loginUser), ErrorCode.NO_AUTH_ERROR);
        return wikiSpace;
    }

    @Override
    public List<WikiSpaceVis> listVisibleSpaceVis(User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ensurePublicSpace();
        ensurePersonalSpaceForUser(loginUser.getId());
        List<WikiSpace> result = new ArrayList<>();
        result.addAll(this.lambdaQuery().eq(WikiSpace::getType, TYPE_PUBLIC).eq(WikiSpace::getIsDelete, 0).list());
        result.addAll(this.lambdaQuery().eq(WikiSpace::getType, TYPE_PERSONAL).eq(WikiSpace::getOwnerUserId, loginUser.getId()).eq(WikiSpace::getIsDelete, 0).list());

        List<WikiSpaceUser> joined = wikiSpaceUserMapper.selectList(new QueryWrapper<WikiSpaceUser>()
                .eq("userId", loginUser.getId())
                .eq("isDelete", 0));
        if (!joined.isEmpty()) {
            List<Long> teamIds = joined.stream().map(WikiSpaceUser::getSpaceId).collect(Collectors.toList());
            result.addAll(this.lambdaQuery().in(WikiSpace::getId, teamIds).eq(WikiSpace::getType, TYPE_TEAM).eq(WikiSpace::getIsDelete, 0).list());
        }
        return result.stream().map(WikiSpaceVis::objToVis).collect(Collectors.toList());
    }

    @Override
    public List<Long> listVisibleSpaceIds(User loginUser) {
        return listVisibleSpaceVis(loginUser).stream().map(WikiSpaceVis::getId).collect(Collectors.toList());
    }

    @Override
    public Long createTeamSpace(String name, User loginUser) {
        requireAdmin(loginUser);
        ThrowUtils.throwIf(StrUtil.isBlank(name), ErrorCode.PARAMS_ERROR, "团队名称不能为空");
        WikiSpace wikiSpace = new WikiSpace();
        wikiSpace.setType(TYPE_TEAM);
        wikiSpace.setName(name);
        wikiSpace.setOwnerUserId(loginUser.getId());
        wikiSpace.setIsDelete(0);
        this.save(wikiSpace);
        addTeamMember(wikiSpace.getId(), loginUser.getId(), "admin", loginUser);
        return wikiSpace.getId();
    }

    @Override
    public Long addTeamMember(Long spaceId, Long userId, String spaceRole, User loginUser) {
        requireAdmin(loginUser);
        WikiSpace wikiSpace = this.getById(spaceId);
        ThrowUtils.throwIf(wikiSpace == null || !Objects.equals(wikiSpace.getType(), TYPE_TEAM), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(userMapper.selectById(userId) == null, ErrorCode.NOT_FOUND_ERROR);
        String role = StrUtil.blankToDefault(spaceRole, ROLE_EDITOR);
        WikiSpaceUser existing = wikiSpaceUserMapper.selectOne(new QueryWrapper<WikiSpaceUser>()
                .eq("spaceId", spaceId)
                .eq("userId", userId)
                .last("limit 1"));
        if (existing != null) {
            wikiSpaceUserMapper.restoreMember(spaceId, userId, role);
            return existing.getId();
        }
        WikiSpaceUser wikiSpaceUser = new WikiSpaceUser();
        wikiSpaceUser.setSpaceId(spaceId);
        wikiSpaceUser.setUserId(userId);
        wikiSpaceUser.setSpaceRole(role);
        wikiSpaceUser.setIsDelete(0);
        wikiSpaceUserMapper.insert(wikiSpaceUser);
        return wikiSpaceUser.getId();
    }

    @Override
    public Boolean removeTeamMember(Long spaceId, Long userId, User loginUser) {
        requireAdmin(loginUser);
        ThrowUtils.throwIf(spaceId == null || userId == null, ErrorCode.PARAMS_ERROR);
        return wikiSpaceUserMapper.delete(new QueryWrapper<WikiSpaceUser>().eq("spaceId", spaceId).eq("userId", userId)) >= 0;
    }

    @Override
    public Boolean exitTeamSpace(Long spaceId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        return wikiSpaceUserMapper.delete(new QueryWrapper<WikiSpaceUser>().eq("spaceId", spaceId).eq("userId", loginUser.getId())) >= 0;
    }

    @Override
    public List<WikiSpaceUserVis> listTeamMembers(Long spaceId, User loginUser) {
        requireAdmin(loginUser);
        List<WikiSpaceUser> members = wikiSpaceUserMapper.selectList(new QueryWrapper<WikiSpaceUser>()
                .eq("spaceId", spaceId)
                .eq("isDelete", 0)
                .orderByDesc("id"));
        return members.stream().map(member -> {
            WikiSpaceUserVis vis = WikiSpaceUserVis.objToVis(member);
            User user = userMapper.selectById(member.getUserId());
            if (user != null) {
                UserVis userVis = new UserVis();
                org.springframework.beans.BeanUtils.copyProperties(user, userVis);
                vis.setUser(userVis);
            }
            return vis;
        }).collect(Collectors.toList());
    }

    @Override
    public List<WikiSpaceVis> listManageTeamSpaces(User loginUser) {
        requireAdmin(loginUser);
        return this.lambdaQuery().eq(WikiSpace::getType, TYPE_TEAM).list().stream()
                .map(WikiSpaceVis::objToVis)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteTeamSpace(Long spaceId, Boolean confirm, User loginUser) {
        requireAdmin(loginUser);
        ThrowUtils.throwIf(!Boolean.TRUE.equals(confirm), ErrorCode.PARAMS_ERROR, "请确认删除");
        WikiSpace wikiSpace = this.getById(spaceId);
        ThrowUtils.throwIf(wikiSpace == null || !Objects.equals(wikiSpace.getType(), TYPE_TEAM), ErrorCode.PARAMS_ERROR);
        long folderCount = wikiFolderMapper.selectCount(new QueryWrapper<com.et.cloud.model.entity.WikiFolder>().eq("spaceId", spaceId));
        long documentCount = documentWikiMapper.selectCount(new QueryWrapper<com.et.cloud.model.entity.DocumentWiki>().eq("spaceId", spaceId));
        if (folderCount == 0 && documentCount == 0) {
            baseMapper.physicallyDeleteById(spaceId);
            wikiSpaceUserMapper.physicallyDeleteBySpaceId(spaceId);
            return true;
        }
        Date now = new Date();
        wikiSpace.setIsDelete(1);
        this.updateById(wikiSpace);
        wikiFolderMapper.logicalDeleteBySpaceId(spaceId, now, loginUser.getId());
        documentWikiMapper.logicalDeleteBySpaceId(spaceId, now, loginUser.getId());
        wikiCacheManager.clearSpace(spaceId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean restoreTeamSpace(Long spaceId, User loginUser) {
        requireAdmin(loginUser);
        WikiSpace wikiSpace = baseMapper.selectByIdIncludeDeleted(spaceId);
        ThrowUtils.throwIf(wikiSpace == null || !Objects.equals(wikiSpace.getType(), TYPE_TEAM), ErrorCode.PARAMS_ERROR);
        baseMapper.restoreById(spaceId);
        wikiFolderMapper.restoreBySpaceId(spaceId);
        documentWikiMapper.restoreBySpaceId(spaceId);
        wikiCacheManager.clearSpace(spaceId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean permanentDeleteTeamSpace(Long spaceId, Boolean confirm, User loginUser) {
        requireAdmin(loginUser);
        ThrowUtils.throwIf(!Boolean.TRUE.equals(confirm), ErrorCode.PARAMS_ERROR, "请确认永久删除");
        WikiSpace wikiSpace = baseMapper.selectByIdIncludeDeleted(spaceId);
        ThrowUtils.throwIf(wikiSpace == null || !Objects.equals(wikiSpace.getType(), TYPE_TEAM) || !Objects.equals(wikiSpace.getIsDelete(), 1), ErrorCode.PARAMS_ERROR);
        documentWikiMapper.physicallyDeleteBySpaceId(spaceId);
        wikiFolderMapper.physicallyDeleteBySpaceId(spaceId);
        wikiSpaceUserMapper.physicallyDeleteBySpaceId(spaceId);
        baseMapper.physicallyDeleteById(spaceId);
        wikiCacheManager.clearSpace(spaceId);
        return true;
    }

    private void requireAdmin(User loginUser) {
        ThrowUtils.throwIf(!isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
    }

    private boolean isAdmin(User user) {
        return user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole());
    }
}

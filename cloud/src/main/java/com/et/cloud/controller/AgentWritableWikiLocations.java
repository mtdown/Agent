package com.et.cloud.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.mapper.WikiFolderMapper;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiFolder;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiSpaceService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** A small, user-scoped directory for Agent destination selection. No document text is loaded. */
@Component
public final class AgentWritableWikiLocations {
    private final UserService users;
    private final WikiSpaceService spaces;
    private final WikiFolderMapper folders;

    public AgentWritableWikiLocations(UserService users, WikiSpaceService spaces, WikiFolderMapper folders) {
        this.users = users;
        this.spaces = spaces;
        this.folders = folders;
    }

    public Map<String, Object> list(long userId, String rawSpaceId, String rawCursor, Integer requestedLimit) {
        User user = users.getById(userId);
        if (user == null) throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        int limit = requestedLimit == null ? 50 : requestedLimit;
        if (limit < 1 || limit > 100) throw new BusinessException(ErrorCode.PARAMS_ERROR, "limit must be 1..100");
        if (rawSpaceId == null && rawCursor != null)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "cursor requires spaceId");

        List<Long> visibleIds = spaces.listVisibleSpaceIds(user);
        List<WikiSpace> writable = visibleIds.isEmpty() ? new ArrayList<>() : spaces.listByIds(visibleIds).stream()
                .filter(space -> spaces.checkSpaceAccess(space, user, true))
                .collect(Collectors.toList());
        Long personalId = writable.stream().filter(space -> Objects.equals(WikiSpaceService.TYPE_PERSONAL, space.getType())
                && Objects.equals(userId, space.getOwnerUserId())).map(WikiSpace::getId).findFirst().orElse(null);
        List<Map<String, String>> spaceItems = writable.stream().map(space -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", space.getId().toString());
            item.put("name", space.getName());
            item.put("type", spaceType(space.getType()));
            return item;
        }).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("defaultPersonalSpaceId", personalId == null ? null : personalId.toString());
        result.put("spaces", spaceItems);
        result.put("folders", new ArrayList<>());
        result.put("nextCursor", null);
        if (rawSpaceId == null) return result;

        long spaceId = positiveId(rawSpaceId);
        // Recheck the requested target even if the earlier directory snapshot was stale.
        spaces.requireSpaceAccess(spaceId, user, true);
        long cursor = rawCursor == null ? 0 : positiveId(rawCursor);
        List<WikiFolder> page = folders.selectList(new QueryWrapper<WikiFolder>()
                .eq("spaceId", spaceId).eq("isDelete", 0).gt("id", cursor)
                .orderByAsc("id").last("limit " + (limit + 1)));
        boolean more = page.size() > limit;
        if (more) page = page.subList(0, limit);
        List<Map<String, String>> folderItems = page.stream().map(folder -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", folder.getId().toString());
            item.put("spaceId", folder.getSpaceId().toString());
            item.put("parentId", folder.getParentId() == null ? null : folder.getParentId().toString());
            item.put("name", folder.getName());
            return item;
        }).collect(Collectors.toList());
        result.put("folders", folderItems);
        result.put("nextCursor", more ? page.get(page.size() - 1).getId().toString() : null);
        return result;
    }

    private static long positiveId(String raw) {
        if (raw == null || !raw.matches("[1-9][0-9]{0,18}"))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid space or cursor ID");
        try { return Long.parseLong(raw); }
        catch (NumberFormatException e) { throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid space or cursor ID"); }
    }

    private static String spaceType(Integer type) {
        if (Integer.valueOf(WikiSpaceService.TYPE_PERSONAL).equals(type)) return "personal";
        if (Integer.valueOf(WikiSpaceService.TYPE_TEAM).equals(type)) return "team";
        return "public";
    }
}

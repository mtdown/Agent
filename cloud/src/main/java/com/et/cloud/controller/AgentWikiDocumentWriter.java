package com.et.cloud.controller;

import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiCacheManager;
import com.et.cloud.service.WikiSpaceService;
import org.springframework.stereotype.Component;

import java.util.Date;

/** Creates a knowledge document only after the current user passes space edit authorization. */
@Component
public final class AgentWikiDocumentWriter {
    private final UserService users;
    private final WikiSpaceService spaces;
    private final DocumentWikiService documents;
    private final WikiCacheManager cache;

    public AgentWikiDocumentWriter(UserService users, WikiSpaceService spaces,
                                   DocumentWikiService documents, WikiCacheManager cache) {
        this.users = users; this.spaces = spaces; this.documents = documents; this.cache = cache;
    }

    public String create(long userId, String rawSpaceId, String title, String content) {
        long spaceId;
        try { spaceId = Long.parseLong(rawSpaceId); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid knowledge space ID"); }
        if (spaceId < 1) throw new IllegalArgumentException("Invalid knowledge space ID");
        User user = users.getById(userId);
        if (user == null) throw new IllegalArgumentException("Current user does not exist");
        WikiSpace space = spaces.requireEditableSpace(spaceId, user);
        DocumentWiki document = new DocumentWiki();
        document.setTitle(title);
        document.setContent(content);
        document.setSummary(documents.buildSummary(content));
        document.setTags("[]");
        document.setContentFormat("markdown");
        document.setSourceType("NATIVE");
        document.setUserId(user.getId());
        document.setSpaceId(space.getId());
        document.setViewCount(0L);
        document.setEditTime(new Date());
        documents.validDocumentWiki(document);
        if (!documents.save(document) || document.getId() == null)
            throw new IllegalStateException("Knowledge document was not saved");
        cache.clearSpace(space.getId());
        return Long.toString(document.getId());
    }
}

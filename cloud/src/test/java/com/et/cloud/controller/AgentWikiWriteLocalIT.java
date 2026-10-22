package com.et.cloud.controller;

import com.et.cloud.dto.documentWiki.DocumentWikiAddRequest;
import com.et.cloud.dto.documentWiki.DocumentWikiEditRequest;
import com.et.cloud.dto.documentWiki.DocumentWikiMoveRequest;
import com.et.cloud.dto.wikirecycle.WikiRecycleActionRequest;
import com.et.cloud.commen.DeleteRequest;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.model.constant.UserConstant;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiFolder;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.WikiFolderService;
import com.et.cloud.service.WikiSpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against this machine's development MySQL and rolls every fixture back. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "agent.internal-key=local-test-secret")
@ActiveProfiles("local")
@Transactional
class AgentWikiWriteLocalIT {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WikiSpaceService spaces;
    @Autowired private WikiFolderService folders;
    @Autowired private AgentWritableWikiLocations directory;
    @Autowired private AgentResourceInternalController internal;
    @Autowired private DocumentWikiController documents;
    @Autowired private WikiRecycleController recycle;

    private User actor;
    private User other;
    private WikiSpace personal;
    private WikiSpace publicSpace;
    private WikiSpace viewerTeam;
    private WikiSpace editorTeam;

    @BeforeEach void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:mysql://localhost:3307/Cloud?"),
                    "Integration test may only use the local development database");
        }
        actor = user();
        other = user();
        publicSpace = spaces.ensurePublicSpace();
        personal = spaces.ensurePersonalSpaceForUser(actor.getId());
        spaces.ensurePersonalSpaceForUser(other.getId());
        viewerTeam = team("viewer");
        editorTeam = team("editor");
        jdbc.update("INSERT INTO wiki_space_user(spaceId,userId,spaceRole,isDelete) VALUES(?,?,?,0)",
                viewerTeam.getId(), actor.getId(), "viewer");
        jdbc.update("INSERT INTO wiki_space_user(spaceId,userId,spaceRole,isDelete) VALUES(?,?,?,0)",
                editorTeam.getId(), actor.getId(), "editor");
    }

    @Test void permissionAndWriteEntryPointsUseActualSpace() {
        assertTrue(spaces.checkSpaceAccess(publicSpace, actor, true));
        assertTrue(spaces.checkSpaceAccess(personal, actor, true));
        assertFalse(spaces.checkSpaceAccess(personal, other, true));
        assertTrue(spaces.checkSpaceAccess(viewerTeam, actor, false));
        assertFalse(spaces.checkSpaceAccess(viewerTeam, actor, true));
        assertTrue(spaces.checkSpaceAccess(editorTeam, actor, true));

        Long folderId = folders.createFolder(personal.getId(), null, "local-it", actor);
        assertNotNull(folderId);
        assertThrows(BusinessException.class,
                () -> folders.requireFolderAccess(folderId, editorTeam.getId(), actor, true));
        assertThrows(BusinessException.class,
                () -> folders.createFolder(viewerTeam.getId(), null, "denied", actor));
        Long publicFolderId = folders.createFolder(publicSpace.getId(), null, "public-folder", other);
        assertTrue(folders.renameFolder(publicFolderId, "renamed-by-another-user", actor));

        DocumentWikiAddRequest add = new DocumentWikiAddRequest();
        add.setSpaceId(viewerTeam.getId());
        add.setTitle("denied");
        add.setContent("body");
        assertThrows(BusinessException.class,
                () -> documents.addDocumentWiki(add, request(actor)));
        add.setSpaceId(publicSpace.getId());
        add.setTitle("public-local-it");
        assertNotNull(documents.addDocumentWiki(add, request(actor)).getData());

        User platformAdmin = user();
        platformAdmin.setUserRole(UserConstant.ADMIN_ROLE);
        jdbc.update("UPDATE user SET userRole=? WHERE id=?", UserConstant.ADMIN_ROLE, platformAdmin.getId());
        assertTrue(spaces.checkSpaceAccess(personal, platformAdmin, true));
        jdbc.update("UPDATE wiki_space_user SET spaceRole='admin' WHERE spaceId=? AND userId=?",
                viewerTeam.getId(), actor.getId());
        assertTrue(spaces.checkSpaceAccess(viewerTeam, actor, true));
        jdbc.update("UPDATE wiki_space SET isDelete=1 WHERE id=?", viewerTeam.getId());
        viewerTeam.setIsDelete(1);
        assertFalse(spaces.checkSpaceAccess(viewerTeam, actor, true));
        assertThrows(BusinessException.class,
                () -> spaces.requireSpaceAccess(viewerTeam.getId(), actor, true));
    }

    @Test void directoryReturnsOnlyWritablePlacesAndPaginatesActiveFolders() {
        Long first = folders.createFolder(personal.getId(), null, "first", actor);
        Long second = folders.createFolder(personal.getId(), null, "second", actor);
        Long deleted = folders.createFolder(personal.getId(), null, "deleted", actor);
        jdbc.update("UPDATE wiki_folder SET isDelete=1 WHERE id=?", deleted);
        assertThrows(BusinessException.class,
                () -> folders.requireFolderAccess(deleted, personal.getId(), actor, true));

        Map<String, Object> listed = directory.list(actor.getId(), null, null, null);
        assertEquals(personal.getId().toString(), listed.get("defaultPersonalSpaceId"));
        List<?> writable = (List<?>) listed.get("spaces");
        assertTrue(containsId(writable, publicSpace.getId()));
        assertTrue(containsId(writable, personal.getId()));
        assertTrue(containsId(writable, editorTeam.getId()));
        assertFalse(containsId(writable, viewerTeam.getId()));
        assertFalse(containsId(writable, spaces.ensurePersonalSpaceForUser(other.getId()).getId()));

        Map<String, Object> page1 = directory.list(actor.getId(), personal.getId().toString(), null, 1);
        assertEquals(1, ((List<?>) page1.get("folders")).size());
        assertNotNull(page1.get("nextCursor"));
        Map<String, Object> page2 = directory.list(actor.getId(), personal.getId().toString(),
                (String) page1.get("nextCursor"), 1);
        assertEquals(1, ((List<?>) page2.get("folders")).size());
        assertNull(page2.get("nextCursor"));
        assertTrue(containsId((List<?>) page1.get("folders"), first)
                || containsId((List<?>) page2.get("folders"), first));
        assertTrue(containsId((List<?>) page1.get("folders"), second)
                || containsId((List<?>) page2.get("folders"), second));
        assertFalse(containsId((List<?>) page1.get("folders"), deleted));
        assertFalse(containsId((List<?>) page2.get("folders"), deleted));
        assertThrows(BusinessException.class,
                () -> directory.list(actor.getId(), viewerTeam.getId().toString(), null, 20));

        Map<String, Object> internalResult = internal.listWritableWikiLocations(
                "local-test-secret", actor.getId().toString(), null, null, null).getData();
        assertEquals(personal.getId().toString(), internalResult.get("defaultPersonalSpaceId"));
        assertThrows(ResponseStatusException.class, () -> internal.listWritableWikiLocations(
                "wrong-key", actor.getId().toString(), null, null, null));
    }

    @Test void publicDocumentCanBeEditedAndRestoredByAnotherUserButViewerCannotMove() {
        DocumentWikiAddRequest add = new DocumentWikiAddRequest();
        add.setSpaceId(publicSpace.getId());
        add.setTitle("public-lifecycle");
        add.setContent("first body");
        Long documentId = documents.addDocumentWiki(add, request(actor)).getData();

        DocumentWikiEditRequest edit = new DocumentWikiEditRequest();
        edit.setId(documentId);
        edit.setTitle("updated by another user");
        edit.setContent("second body");
        edit.setTags(List.of());
        assertTrue(documents.editDocumentWiki(edit, request(other)).getData());

        DocumentWikiMoveRequest move = new DocumentWikiMoveRequest();
        move.setId(documentId);
        move.setTargetSpaceId(viewerTeam.getId());
        assertThrows(BusinessException.class, () -> documents.moveDocumentWiki(move, request(actor)));

        DeleteRequest delete = new DeleteRequest();
        delete.setId(documentId);
        assertTrue(documents.deleteDocumentWiki(delete, request(other)).getData());
        WikiRecycleActionRequest restore = new WikiRecycleActionRequest();
        restore.setSpaceId(publicSpace.getId());
        restore.setItemId(documentId);
        restore.setItemType("document");
        assertTrue(recycle.restore(restore, request(other)).getData());
        restore.setSpaceId(personal.getId());
        assertThrows(BusinessException.class, () -> recycle.restore(restore, request(other)));

        add.setSpaceId(editorTeam.getId());
        add.setTitle("team-source");
        Long teamDocumentId = documents.addDocumentWiki(add, request(actor)).getData();
        jdbc.update("UPDATE wiki_space_user SET spaceRole='viewer' WHERE spaceId=? AND userId=?",
                editorTeam.getId(), actor.getId());
        move.setId(teamDocumentId);
        move.setTargetSpaceId(publicSpace.getId());
        assertThrows(BusinessException.class, () -> documents.moveDocumentWiki(move, request(actor)));
    }

    private User user() {
        String account = "wiki-it-" + UUID.randomUUID();
        jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)",
                account, "test-only", UserConstant.DEFAULT_ROLE);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        User result = new User(); result.setId(id); result.setUserRole(UserConstant.DEFAULT_ROLE);
        return result;
    }

    private WikiSpace team(String name) {
        jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(1,?,?,0)",
                "local-it-" + name + "-" + UUID.randomUUID(), actor.getId());
        return spaces.getById(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
    }

    private static boolean containsId(List<?> items, Long id) {
        return items.stream().anyMatch(item -> id.toString().equals(((Map<?, ?>) item).get("id")));
    }

    private static MockHttpServletRequest request(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        return request;
    }
}

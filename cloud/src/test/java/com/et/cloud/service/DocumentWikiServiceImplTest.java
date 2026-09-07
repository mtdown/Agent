package com.et.cloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.et.cloud.dto.documentWiki.DocumentWikiQueryRequest;
import com.et.cloud.dto.documentWiki.DocumentWikiAddRequest;
import com.et.cloud.dto.documentWiki.DocumentWikiEditRequest;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.vis.DocumentWikiVis;
import com.et.cloud.model.vis.UserVis;
import com.et.cloud.service.impl.DocumentWikiServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentWikiServiceImplTest {

    private final DocumentWikiServiceImpl documentWikiService = new DocumentWikiServiceImpl();

    @Test
    void validDocumentWikiRejectsBlankTitle() {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setTitle(" ");
        documentWiki.setContent("valid content");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentWikiService.validDocumentWiki(documentWiki)
        );

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void validDocumentWikiAcceptsHtmlFormat() {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setTitle("Valid title");
        documentWiki.setContent("<p><strong>valid content</strong></p>");
        documentWiki.setSpaceId(1L);
        documentWiki.setContentFormat("html");

        assertDoesNotThrow(() -> documentWikiService.validDocumentWiki(documentWiki));
    }

    @Test
    void validDocumentWikiRejectsIllegalContentFormat() {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setTitle("Valid title");
        documentWiki.setContent("valid content");
        documentWiki.setSpaceId(1L);
        documentWiki.setContentFormat("weird");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentWikiService.validDocumentWiki(documentWiki)
        );

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void validDocumentWikiRejectsIllegalSourceType() {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setTitle("Valid title");
        documentWiki.setContent("valid content");
        documentWiki.setSpaceId(1L);
        documentWiki.setSourceType("WEIRD");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentWikiService.validDocumentWiki(documentWiki)
        );

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void validDocumentWikiAcceptsMarkdownFormatAndNativeSource() {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setTitle("Valid title");
        documentWiki.setContent("valid content");
        documentWiki.setSpaceId(1L);
        documentWiki.setContentFormat("markdown");
        documentWiki.setSourceType("NATIVE");

        assertDoesNotThrow(() -> documentWikiService.validDocumentWiki(documentWiki));
    }

    @Test
    void documentWikiRequestsCarryWikiFirstReservedFields() {
        DocumentWikiAddRequest addRequest = new DocumentWikiAddRequest();
        addRequest.setContentFormat("markdown");
        addRequest.setSourceType("URL");
        addRequest.setSourceUrl("https://example.com/source");
        addRequest.setContentHash("abc123");
        addRequest.setContentVersion(2);
        addRequest.setVisibility("SPACE");
        addRequest.setMetadataJson("{\"from\":\"test\"}");

        DocumentWikiEditRequest editRequest = new DocumentWikiEditRequest();
        editRequest.setContentFormat(addRequest.getContentFormat());
        editRequest.setSourceType(addRequest.getSourceType());
        editRequest.setSourceUrl(addRequest.getSourceUrl());
        editRequest.setContentHash(addRequest.getContentHash());
        editRequest.setContentVersion(addRequest.getContentVersion());
        editRequest.setVisibility(addRequest.getVisibility());
        editRequest.setMetadataJson(addRequest.getMetadataJson());

        assertEquals(addRequest.getContentHash(), editRequest.getContentHash());
        assertEquals(addRequest.getContentVersion(), editRequest.getContentVersion());
        assertEquals(addRequest.getVisibility(), editRequest.getVisibility());
    }

    @Test
    void validDocumentWikiRejectsBlankContent() {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setTitle("Valid title");
        documentWiki.setContent(" ");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentWikiService.validDocumentWiki(documentWiki)
        );

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void getQueryWrapperBuildsSearchAndFilterConditions() {
        DocumentWikiQueryRequest request = new DocumentWikiQueryRequest();
        request.setMatchMode("titleOrContent");
        request.setTitle("cache");
        request.setSummary("summary");
        request.setTags(Arrays.asList("java", "wiki"));
        request.setSpaceId(3L);
        request.setVisibleSpaceIds(Arrays.asList(1L, 3L));
        request.setUserId(1L);
        request.setSortField("editTime");
        request.setSortOrder("ascend");

        QueryWrapper<DocumentWiki> queryWrapper = documentWikiService.getQueryWrapper(request);
        String sqlSegment = queryWrapper.getSqlSegment();

        assertTrue(sqlSegment.contains("title"));
        assertTrue(sqlSegment.contains("summary"));
        assertTrue(sqlSegment.contains("spaceId"));
        assertTrue(sqlSegment.contains("isDelete"));
        assertTrue(sqlSegment.contains("tags"));
        assertTrue(sqlSegment.contains("userId"));
        assertTrue(sqlSegment.contains("ORDER BY editTime ASC"));
    }

    @Test
    void getQueryWrapperUsesFullTextSearchWhenSearchTextPresent() {
        DocumentWikiQueryRequest request = new DocumentWikiQueryRequest();
        request.setSearchText("数据库设计");
        request.setVisibleSpaceIds(Arrays.asList(1L, 3L));

        QueryWrapper<DocumentWiki> queryWrapper = documentWikiService.getQueryWrapper(request);
        String sqlSegment = queryWrapper.getSqlSegment();

        assertTrue(sqlSegment.contains("MATCH (title, content) AGAINST"));
        assertTrue(sqlSegment.contains("IN NATURAL LANGUAGE MODE"));
        assertTrue(sqlSegment.contains("spaceId IN"));
        assertTrue(sqlSegment.contains("ORDER BY MATCH (title, content) AGAINST"));
        assertFalse(sqlSegment.contains("content LIKE"));
        assertFalse(sqlSegment.contains("title LIKE"));
    }

    @Test
    void getQueryWrapperOrdersPlainListByEditTimeDesc() {
        DocumentWikiQueryRequest request = new DocumentWikiQueryRequest();
        request.setVisibleSpaceIds(Arrays.asList(1L, 3L));

        QueryWrapper<DocumentWiki> queryWrapper = documentWikiService.getQueryWrapper(request);
        String sqlSegment = queryWrapper.getSqlSegment();

        assertTrue(sqlSegment.contains("ORDER BY editTime DESC"));
        assertFalse(sqlSegment.contains("MATCH (title, content) AGAINST"));
    }

    @Test
    void getQueryWrapperBlocksSearchWhenNoVisibleSpaceExists() {
        DocumentWikiQueryRequest request = new DocumentWikiQueryRequest();
        request.setSearchText("数据库设计");
        request.setVisibleSpaceIds(Arrays.asList());

        QueryWrapper<DocumentWiki> queryWrapper = documentWikiService.getQueryWrapper(request);
        String sqlSegment = queryWrapper.getSqlSegment();

        assertTrue(sqlSegment.contains("MATCH (title, content) AGAINST"));
        assertTrue(sqlSegment.contains("1 = 0"));
    }

    @Test
    void objToVisConvertsJsonTags() {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setId(1L);
        documentWiki.setTitle("Agent Wiki");
        documentWiki.setContent("Document content");
        documentWiki.setTags("[\"java\",\"wiki\"]");

        DocumentWikiVis documentWikiVis = DocumentWikiVis.objToVis(documentWiki);

        assertEquals(documentWiki.getId(), documentWikiVis.getId());
        assertEquals(documentWiki.getTitle(), documentWikiVis.getTitle());
        assertEquals(Arrays.asList("java", "wiki"), documentWikiVis.getTags());
    }

    @Test
    void getDocumentWikiVisPageLoadsAuthorsInOneBatch() {
        UserService userService = mock(UserService.class);
        ReflectionTestUtils.setField(documentWikiService, "userService", userService);
        User user1 = user(1L, "author1");
        User user2 = user(2L, "author2");
        UserVis userVis1 = userVis(1L, "author1");
        UserVis userVis2 = userVis(2L, "author2");
        when(userService.listByIds(Arrays.asList(1L, 2L))).thenReturn(Arrays.asList(user1, user2));
        when(userService.getUserVis(user1)).thenReturn(userVis1);
        when(userService.getUserVis(user2)).thenReturn(userVis2);
        Page<DocumentWiki> page = documentPage(
                document(1L, 1L),
                document(2L, 1L),
                document(3L, 2L)
        );

        Page<DocumentWikiVis> result = documentWikiService.getDocumentWikiVisPage(page, null);

        assertEquals(3, result.getRecords().size());
        assertEquals(1L, result.getRecords().get(0).getUser().getId());
        assertEquals(1L, result.getRecords().get(1).getUser().getId());
        assertEquals(2L, result.getRecords().get(2).getUser().getId());
        verify(userService).listByIds(Arrays.asList(1L, 2L));
        verify(userService, never()).getById(any());
    }

    @Test
    void getDocumentWikiVisPageDoesNotQueryAuthorsForEmptyPage() {
        UserService userService = mock(UserService.class);
        ReflectionTestUtils.setField(documentWikiService, "userService", userService);
        Page<DocumentWiki> page = new Page<>(1, 20, 0);
        page.setRecords(Collections.emptyList());

        Page<DocumentWikiVis> result = documentWikiService.getDocumentWikiVisPage(page, null);

        assertTrue(result.getRecords().isEmpty());
        verify(userService, never()).listByIds(any());
        verify(userService, never()).getById(any());
    }

    @Test
    void getDocumentWikiVisPageLeavesMissingAuthorNull() {
        UserService userService = mock(UserService.class);
        ReflectionTestUtils.setField(documentWikiService, "userService", userService);
        when(userService.listByIds(Collections.singletonList(99L))).thenReturn(Collections.emptyList());
        Page<DocumentWiki> page = documentPage(document(1L, 99L), document(2L, null));

        Page<DocumentWikiVis> result = documentWikiService.getDocumentWikiVisPage(page, null);

        assertEquals(2, result.getRecords().size());
        assertNull(result.getRecords().get(0).getUser());
        assertNull(result.getRecords().get(1).getUser());
        verify(userService).listByIds(Collections.singletonList(99L));
        verify(userService, never()).getById(any());
    }

    private Page<DocumentWiki> documentPage(DocumentWiki... documents) {
        Page<DocumentWiki> page = new Page<>(1, documents.length, documents.length);
        page.setRecords(Arrays.asList(documents));
        return page;
    }

    private DocumentWiki document(Long id, Long userId) {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setId(id);
        documentWiki.setTitle("title " + id);
        documentWiki.setContent("content " + id);
        documentWiki.setUserId(userId);
        return documentWiki;
    }

    private User user(Long id, String userName) {
        User user = new User();
        user.setId(id);
        user.setUserName(userName);
        return user;
    }

    private UserVis userVis(Long id, String userName) {
        UserVis userVis = new UserVis();
        userVis.setId(id);
        userVis.setUserName(userName);
        return userVis;
    }
}

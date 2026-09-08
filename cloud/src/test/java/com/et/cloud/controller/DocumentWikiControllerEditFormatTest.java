package com.et.cloud.controller;

import com.et.cloud.dto.documentWiki.DocumentWikiEditRequest;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiCacheManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentWikiControllerEditFormatTest {

    @Test
    void editHtmlDocumentIsRejectedAsPreviewOnly() {
        DocumentWikiController controller = new DocumentWikiController();
        DocumentWikiService documentWikiService = mock(DocumentWikiService.class);
        UserService userService = mock(UserService.class);
        WikiCacheManager wikiCacheManager = mock(WikiCacheManager.class);
        ReflectionTestUtils.setField(controller, "documentWikiService", documentWikiService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "wikiCacheManager", wikiCacheManager);
        User loginUser = user(7L);
        DocumentWiki oldDocument = storedDocument(99L, null);
        DocumentWikiEditRequest request = new DocumentWikiEditRequest();
        request.setId(99L);
        request.setTitle("HTML doc");
        request.setContent("<h1 onclick=\"evil()\">Title</h1><script>alert(1)</script>");
        request.setContentFormat("html");
        request.setTags(Collections.emptyList());

        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(documentWikiService.getById(99L)).thenReturn(oldDocument);
        doNothing().when(documentWikiService).checkDocumentWikiVisible(loginUser, oldDocument);

        assertThrows(BusinessException.class,
                () -> controller.editDocumentWiki(request, new MockHttpServletRequest()));
        verify(documentWikiService, never()).updateById(any());
        verify(wikiCacheManager, never()).clearDocument(any(), any());
    }

    @Test
    void editUploadedHtmlDocumentIsRejectedWhenRequestOmitsFormat() {
        DocumentWikiController controller = new DocumentWikiController();
        DocumentWikiService documentWikiService = mock(DocumentWikiService.class);
        UserService userService = mock(UserService.class);
        WikiCacheManager wikiCacheManager = mock(WikiCacheManager.class);
        ReflectionTestUtils.setField(controller, "documentWikiService", documentWikiService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "wikiCacheManager", wikiCacheManager);
        User loginUser = user(7L);
        DocumentWiki oldDocument = storedDocument(100L, "html");
        DocumentWikiEditRequest request = new DocumentWikiEditRequest();
        request.setId(100L);
        request.setTitle("HTML doc");
        request.setContent("<p onclick=\"evil()\">Body</p>");
        request.setTags(Collections.emptyList());

        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(documentWikiService.getById(100L)).thenReturn(oldDocument);
        doNothing().when(documentWikiService).checkDocumentWikiVisible(loginUser, oldDocument);

        assertThrows(BusinessException.class,
                () -> controller.editDocumentWiki(request, new MockHttpServletRequest()));
        verify(documentWikiService, never()).updateById(any());
    }

    @Test
    void editMarkdownDocumentKeepsStoredMetadataAndClearsCache() {
        DocumentWikiController controller = new DocumentWikiController();
        DocumentWikiService documentWikiService = mock(DocumentWikiService.class);
        UserService userService = mock(UserService.class);
        WikiCacheManager wikiCacheManager = mock(WikiCacheManager.class);
        ReflectionTestUtils.setField(controller, "documentWikiService", documentWikiService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "wikiCacheManager", wikiCacheManager);
        User loginUser = user(7L);
        DocumentWiki oldDocument = storedDocument(101L, "markdown");
        DocumentWikiEditRequest request = new DocumentWikiEditRequest();
        request.setId(101L);
        request.setTitle("Markdown doc");
        request.setContent("# Title\n\nBody");
        request.setContentFormat("markdown");
        request.setTags(Collections.emptyList());

        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(documentWikiService.getById(101L)).thenReturn(oldDocument);
        doNothing().when(documentWikiService).checkDocumentWikiVisible(loginUser, oldDocument);
        when(documentWikiService.buildSummary("# Title\n\nBody")).thenReturn("Title Body");
        when(documentWikiService.updateById(any())).thenReturn(true);

        controller.editDocumentWiki(request, new MockHttpServletRequest());

        ArgumentCaptor<DocumentWiki> captor = ArgumentCaptor.forClass(DocumentWiki.class);
        verify(documentWikiService).updateById(captor.capture());
        assertEquals("# Title\n\nBody", captor.getValue().getContent());
        assertEquals("markdown", captor.getValue().getContentFormat());
        assertEquals("UPLOAD", captor.getValue().getSourceType());
        assertEquals("{\"sourceFileName\":\"source.md\"}", captor.getValue().getMetadataJson());
        assertEquals("Title Body", captor.getValue().getSummary());
        verify(wikiCacheManager).clearDocument(11L, 101L);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private DocumentWiki storedDocument(Long id, String contentFormat) {
        DocumentWiki document = new DocumentWiki();
        document.setId(id);
        document.setSpaceId(11L);
        document.setFolderId(22L);
        document.setSourceType("UPLOAD");
        document.setContentFormat(contentFormat);
        document.setMetadataJson("{\"sourceFileName\":\"source.md\"}");
        return document;
    }
}

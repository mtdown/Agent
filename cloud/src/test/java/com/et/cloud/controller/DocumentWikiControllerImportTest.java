package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.model.dto.ImportedWikiDocument;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiFolder;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiCacheManager;
import com.et.cloud.service.WikiDocumentImportService;
import com.et.cloud.service.WikiFolderService;
import com.et.cloud.service.WikiSpaceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentWikiControllerImportTest {

    @Test
    void importDocumentCreatesWikiDocumentAndClearsSpaceCache() {
        DocumentWikiController controller = newController();
        DocumentWikiService documentWikiService = mock(DocumentWikiService.class);
        UserService userService = mock(UserService.class);
        WikiSpaceService wikiSpaceService = mock(WikiSpaceService.class);
        WikiFolderService wikiFolderService = mock(WikiFolderService.class);
        WikiDocumentImportService importService = mock(WikiDocumentImportService.class);
        WikiCacheManager wikiCacheManager = mock(WikiCacheManager.class);
        ReflectionTestUtils.setField(controller, "documentWikiService", documentWikiService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "wikiSpaceService", wikiSpaceService);
        ReflectionTestUtils.setField(controller, "wikiFolderService", wikiFolderService);
        ReflectionTestUtils.setField(controller, "wikiDocumentImportService", importService);
        ReflectionTestUtils.setField(controller, "wikiCacheManager", wikiCacheManager);
        User loginUser = user(7L);
        WikiSpace wikiSpace = new WikiSpace();
        wikiSpace.setId(11L);
        WikiFolder folder = new WikiFolder();
        folder.setId(22L);
        ImportedWikiDocument imported = importedDocument();
        MockMultipartFile file = file("page.html", "<h1>Title</h1>");

        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(wikiSpaceService.requireEditableSpace(11L, loginUser)).thenReturn(wikiSpace);
        when(wikiFolderService.requireVisibleFolder(22L, 11L, loginUser)).thenReturn(folder);
        when(importService.parse(file, "Custom title")).thenReturn(imported);
        when(documentWikiService.save(any())).thenAnswer(invocation -> {
            DocumentWiki documentWiki = invocation.getArgument(0);
            documentWiki.setId(99L);
            return true;
        });

        BaseResponse<Long> response = controller.importDocumentWiki(file, 11L, 22L, "Custom title", new MockHttpServletRequest());

        assertEquals(99L, response.getData());
        ArgumentCaptor<DocumentWiki> captor = ArgumentCaptor.forClass(DocumentWiki.class);
        verify(documentWikiService).save(captor.capture());
        DocumentWiki saved = captor.getValue();
        assertEquals("Custom title", saved.getTitle());
        assertEquals("# Title", saved.getContent());
        assertEquals("markdown", saved.getContentFormat());
        assertEquals("UPLOAD", saved.getSourceType());
        assertEquals(11L, saved.getSpaceId());
        assertEquals(22L, saved.getFolderId());
        assertEquals(7L, saved.getUserId());
        verify(documentWikiService).validDocumentWiki(saved);
        verify(wikiCacheManager).clearSpace(11L);
    }

    @Test
    void importDocumentRejectsFolderFromAnotherSpaceBeforeSaving() {
        DocumentWikiController controller = newController();
        DocumentWikiService documentWikiService = mock(DocumentWikiService.class);
        UserService userService = mock(UserService.class);
        WikiSpaceService wikiSpaceService = mock(WikiSpaceService.class);
        WikiFolderService wikiFolderService = mock(WikiFolderService.class);
        ReflectionTestUtils.setField(controller, "documentWikiService", documentWikiService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "wikiSpaceService", wikiSpaceService);
        ReflectionTestUtils.setField(controller, "wikiFolderService", wikiFolderService);
        User loginUser = user(7L);
        WikiSpace wikiSpace = new WikiSpace();
        wikiSpace.setId(11L);
        MockMultipartFile file = file("page.html", "<h1>Title</h1>");

        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(wikiSpaceService.requireEditableSpace(11L, loginUser)).thenReturn(wikiSpace);
        when(wikiFolderService.requireVisibleFolder(22L, 11L, loginUser))
                .thenThrow(new BusinessException(ErrorCode.PARAMS_ERROR, "文件夹不属于目标空间"));

        assertThrows(BusinessException.class,
                () -> controller.importDocumentWiki(file, 11L, 22L, null, new MockHttpServletRequest()));
        verify(documentWikiService, never()).save(any());
    }

    @Test
    void importDocumentRejectsParserFailureBeforeSaving() {
        DocumentWikiController controller = newController();
        DocumentWikiService documentWikiService = mock(DocumentWikiService.class);
        UserService userService = mock(UserService.class);
        WikiSpaceService wikiSpaceService = mock(WikiSpaceService.class);
        WikiDocumentImportService importService = mock(WikiDocumentImportService.class);
        ReflectionTestUtils.setField(controller, "documentWikiService", documentWikiService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "wikiSpaceService", wikiSpaceService);
        ReflectionTestUtils.setField(controller, "wikiDocumentImportService", importService);
        User loginUser = user(7L);
        WikiSpace wikiSpace = new WikiSpace();
        wikiSpace.setId(11L);
        MockMultipartFile file = file("broken.html", "broken");

        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(wikiSpaceService.requireEditableSpace(11L, loginUser)).thenReturn(wikiSpace);
        when(importService.parse(file, null))
                .thenThrow(new BusinessException(ErrorCode.PARAMS_ERROR, "文件内容不能为空"));

        assertThrows(BusinessException.class,
                () -> controller.importDocumentWiki(file, 11L, null, null, new MockHttpServletRequest()));
        verify(documentWikiService, never()).save(any());
    }

    @Test
    void importDocumentRejectsPermissionFailureBeforeParsing() {
        DocumentWikiController controller = newController();
        DocumentWikiService documentWikiService = mock(DocumentWikiService.class);
        UserService userService = mock(UserService.class);
        WikiSpaceService wikiSpaceService = mock(WikiSpaceService.class);
        WikiDocumentImportService importService = mock(WikiDocumentImportService.class);
        ReflectionTestUtils.setField(controller, "documentWikiService", documentWikiService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "wikiSpaceService", wikiSpaceService);
        ReflectionTestUtils.setField(controller, "wikiDocumentImportService", importService);
        User loginUser = user(7L);
        MockMultipartFile file = file("page.html", "<h1>Title</h1>");

        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(wikiSpaceService.requireEditableSpace(11L, loginUser))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限"));

        assertThrows(BusinessException.class,
                () -> controller.importDocumentWiki(file, 11L, null, null, new MockHttpServletRequest()));
        verify(importService, never()).parse(any(), any());
        verify(documentWikiService, never()).save(any());
    }

    private DocumentWikiController newController() {
        return new DocumentWikiController();
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private ImportedWikiDocument importedDocument() {
        ImportedWikiDocument imported = new ImportedWikiDocument();
        imported.setTitle("Custom title");
        imported.setContent("# Title");
        imported.setContentFormat("markdown");
        imported.setSourceType("UPLOAD");
        imported.setMetadataJson("{\"sourceExtension\":\"html\",\"importedAs\":\"markdown\"}");
        return imported;
    }

    private MockMultipartFile file(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/html", content.getBytes(StandardCharsets.UTF_8));
    }
}

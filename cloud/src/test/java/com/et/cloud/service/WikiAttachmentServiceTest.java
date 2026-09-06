package com.et.cloud.service;

import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.config.CosClientConfig;
import com.et.cloud.manager.CosManager;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiAttachment;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.impl.WikiAttachmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikiAttachmentServiceTest {

    private WikiAttachmentServiceImpl wikiAttachmentService;

    private WikiSpaceService wikiSpaceService;

    private DocumentWikiService documentWikiService;

    private CosManager cosManager;

    private CosClientConfig cosClientConfig;

    @BeforeEach
    void setUp() {
        wikiAttachmentService = spy(new WikiAttachmentServiceImpl());
        wikiSpaceService = mock(WikiSpaceService.class);
        documentWikiService = mock(DocumentWikiService.class);
        cosManager = mock(CosManager.class);
        cosClientConfig = mock(CosClientConfig.class);
        ReflectionTestUtils.setField(wikiAttachmentService, "wikiSpaceService", wikiSpaceService);
        ReflectionTestUtils.setField(wikiAttachmentService, "documentWikiService", documentWikiService);
        ReflectionTestUtils.setField(wikiAttachmentService, "cosManager", cosManager);
        ReflectionTestUtils.setField(wikiAttachmentService, "cosClientConfig", cosClientConfig);
    }

    @Test
    void validImageRejectsOversizeFile() {
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]);

        BusinessException exception = assertThrows(BusinessException.class, () -> wikiAttachmentService.validImage(file));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void validImageRejectsIllegalSuffix() {
        MockMultipartFile file = new MockMultipartFile("file", "evil.pdf", "application/pdf", new byte[10]);

        BusinessException exception = assertThrows(BusinessException.class, () -> wikiAttachmentService.validImage(file));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void validImageRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        BusinessException exception = assertThrows(BusinessException.class, () -> wikiAttachmentService.validImage(file));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void validImageAcceptsPng() {
        MockMultipartFile file = new MockMultipartFile("file", "ok.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});

        assertDoesNotThrow(() -> wikiAttachmentService.validImage(file));
    }

    @Test
    void validImageRejectsDisguisedTextFile() {
        MockMultipartFile file = new MockMultipartFile("file", "evil.png", "image/png",
                "not really an image".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () -> wikiAttachmentService.validImage(file));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void uploadImageRejectsMissingLoginUser() {
        MockMultipartFile file = new MockMultipartFile("file", "ok.png", "image/png", new byte[10]);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> wikiAttachmentService.uploadImage(file, 1L, null, null));

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());
    }

    @Test
    void uploadImageRejectsInvisibleSpace() {
        when(wikiSpaceService.requireEditableSpace(eq(9L), any(User.class)))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR));
        MockMultipartFile file = pngFile("ok.png");
        User loginUser = new User();
        loginUser.setId(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> wikiAttachmentService.uploadImage(file, 9L, null, loginUser));

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());
    }

    @Test
    void uploadImageRejectsViewOnlySpaceBeforeCosWrite() {
        when(wikiSpaceService.requireEditableSpace(eq(9L), any(User.class)))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR));
        MockMultipartFile file = pngFile("viewer.png");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> wikiAttachmentService.uploadImage(file, 9L, null, loginUser()));

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());
        verify(cosManager, never()).putObject(any(), any());
    }

    @Test
    void uploadImageIsTransactional() throws NoSuchMethodException {
        Transactional transactional = WikiAttachmentServiceImpl.class
                .getMethod("uploadImage", org.springframework.web.multipart.MultipartFile.class,
                        Long.class, Long.class, User.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Exception.class, transactional.rollbackFor()[0]);
    }

    @Test
    void uploadImageDeletesCosObjectWhenAttachmentSaveFails() {
        WikiSpace wikiSpace = new WikiSpace();
        wikiSpace.setId(2L);
        when(wikiSpaceService.requireEditableSpace(eq(2L), any(User.class))).thenReturn(wikiSpace);
        when(cosClientConfig.getHost()).thenReturn("https://cdn.example.com");
        doReturn(false).when(wikiAttachmentService).save(any(WikiAttachment.class));
        MockMultipartFile file = pngFile("ok.png");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> wikiAttachmentService.uploadImage(file, 2L, null, loginUser()));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), exception.getCode());
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(cosManager).putObject(pathCaptor.capture(), any());
        verify(cosManager).deleteObject(pathCaptor.getValue());
    }

    @Test
    void uploadImageRejectsCrossSpaceDocumentBeforeCosWrite() {
        WikiSpace wikiSpace = new WikiSpace();
        wikiSpace.setId(2L);
        when(wikiSpaceService.requireEditableSpace(eq(2L), any(User.class))).thenReturn(wikiSpace);
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setId(100L);
        documentWiki.setSpaceId(3L);
        when(documentWikiService.getById(100L)).thenReturn(documentWiki);
        MockMultipartFile file = pngFile("ok.png");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> wikiAttachmentService.uploadImage(file, 2L, 100L, loginUser()));

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());
        verify(cosManager, never()).putObject(any(), any());
    }

    @Test
    void uploadImageAllowsNullDocumentId() {
        WikiSpace wikiSpace = new WikiSpace();
        wikiSpace.setId(2L);
        when(wikiSpaceService.requireEditableSpace(eq(2L), any(User.class))).thenReturn(wikiSpace);
        when(cosClientConfig.getHost()).thenReturn("https://cdn.example.com");
        ArgumentCaptor<WikiAttachment> attachmentCaptor = ArgumentCaptor.forClass(WikiAttachment.class);
        doReturn(true).when(wikiAttachmentService).save(attachmentCaptor.capture());
        MockMultipartFile file = pngFile("ok.png");

        String url = wikiAttachmentService.uploadImage(file, 2L, null, loginUser());

        assertTrue(url.startsWith("https://cdn.example.com/wiki/2/"));
        assertEquals(null, attachmentCaptor.getValue().getDocumentId());
    }

    @Test
    void uploadImageSavesAttachmentForMatchingDocument() {
        WikiSpace wikiSpace = new WikiSpace();
        wikiSpace.setId(2L);
        when(wikiSpaceService.requireEditableSpace(eq(2L), any(User.class))).thenReturn(wikiSpace);
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setId(100L);
        documentWiki.setSpaceId(2L);
        when(documentWikiService.getById(100L)).thenReturn(documentWiki);
        when(cosClientConfig.getHost()).thenReturn("https://cdn.example.com");
        ArgumentCaptor<WikiAttachment> attachmentCaptor = ArgumentCaptor.forClass(WikiAttachment.class);
        doReturn(true).when(wikiAttachmentService).save(attachmentCaptor.capture());
        MockMultipartFile file = pngFile("ok.png");

        String url = wikiAttachmentService.uploadImage(file, 2L, 100L, loginUser());

        WikiAttachment attachment = attachmentCaptor.getValue();
        assertEquals(url, attachment.getUrl());
        assertEquals(2L, attachment.getWikiSpaceId());
        assertEquals(100L, attachment.getDocumentId());
        assertEquals("ok.png", attachment.getFileName());
        assertEquals(8L, attachment.getFileSize());
        assertEquals("image/png", attachment.getMimeType());
        assertNotNull(attachment.getFileHash());
        assertEquals(1L, attachment.getUserId());
    }

    @Test
    void uploadImageNormalizesUppercaseSuffixInObjectPath() {
        WikiSpace wikiSpace = new WikiSpace();
        wikiSpace.setId(2L);
        when(wikiSpaceService.requireEditableSpace(eq(2L), any(User.class))).thenReturn(wikiSpace);
        when(cosClientConfig.getHost()).thenReturn("https://cdn.example.com");
        doReturn(true).when(wikiAttachmentService).save(any(WikiAttachment.class));
        MockMultipartFile file = pngFile("PHOTO.PNG");

        String url = wikiAttachmentService.uploadImage(file, 2L, null, loginUser());

        assertTrue(url.endsWith(".png"));
        verify(cosManager).putObject(org.mockito.ArgumentMatchers.matches("wiki/2/.+\\.png"), any());
    }

    private User loginUser() {
        User loginUser = new User();
        loginUser.setId(1L);
        return loginUser;
    }

    private MockMultipartFile pngFile(String filename) {
        return new MockMultipartFile("file", filename, "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
    }
}

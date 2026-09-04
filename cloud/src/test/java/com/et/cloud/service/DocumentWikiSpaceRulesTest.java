package com.et.cloud.service;

import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.impl.DocumentWikiServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentWikiSpaceRulesTest {

    private WikiSpaceService wikiSpaceService;
    private DocumentWikiServiceImpl documentWikiService;

    @BeforeEach
    void setUp() {
        wikiSpaceService = mock(WikiSpaceService.class);
        documentWikiService = new DocumentWikiServiceImpl();
        ReflectionTestUtils.setField(documentWikiService, "wikiSpaceService", wikiSpaceService);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUserRole("user");
        return user;
    }

    private DocumentWiki document(Long spaceId, Integer isDelete) {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setId(1L);
        documentWiki.setSpaceId(spaceId);
        documentWiki.setTitle("title");
        documentWiki.setContent("content");
        documentWiki.setIsDelete(isDelete);
        return documentWiki;
    }

    @Test
    void checkDocumentWikiVisibleRejectsNullLoginUser() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentWikiService.checkDocumentWikiVisible(null, document(1L, 0))
        );

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());
    }

    @Test
    void checkDocumentWikiVisibleRejectsNullDocument() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentWikiService.checkDocumentWikiVisible(user(1L), null)
        );

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
    }

    @Test
    void checkDocumentWikiVisibleRejectsDeletedDocument() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentWikiService.checkDocumentWikiVisible(user(1L), document(1L, 1))
        );

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
    }

    @Test
    void checkDocumentWikiVisibleDelegatesToSpaceVisibility() {
        when(wikiSpaceService.requireVisibleSpace(any(), any())).thenReturn(new WikiSpace());

        assertDoesNotThrow(() -> documentWikiService.checkDocumentWikiVisible(user(1L), document(1L, 0)));
    }

    @Test
    void checkDocumentWikiVisibleRejectsInvisibleSpace() {
        when(wikiSpaceService.requireVisibleSpace(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentWikiService.checkDocumentWikiVisible(user(1L), document(99L, 0))
        );

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());
    }

    @Test
    void validDocumentWikiAllowsRootDocumentWithoutFolder() {
        DocumentWiki rootDocument = document(1L, 0);
        rootDocument.setFolderId(null);

        assertDoesNotThrow(() -> documentWikiService.validDocumentWiki(rootDocument));
    }

    @Test
    void validDocumentWikiRejectsMissingSpace() {
        DocumentWiki missingSpace = document(null, 0);
        missingSpace.setFolderId(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentWikiService.validDocumentWiki(missingSpace)
        );

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }
}

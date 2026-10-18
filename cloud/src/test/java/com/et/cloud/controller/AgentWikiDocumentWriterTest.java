package com.et.cloud.controller;

import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiCacheManager;
import com.et.cloud.service.WikiSpaceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentWikiDocumentWriterTest {
    @Test void checksEditPermissionBeforeSavingAndClearsSpaceCache() {
        UserService users = mock(UserService.class);
        WikiSpaceService spaces = mock(WikiSpaceService.class);
        DocumentWikiService documents = mock(DocumentWikiService.class);
        WikiCacheManager cache = mock(WikiCacheManager.class);
        User user = new User(); user.setId(7L);
        WikiSpace space = new WikiSpace(); space.setId(12L);
        when(users.getById(7L)).thenReturn(user);
        when(spaces.requireEditableSpace(12L, user)).thenReturn(space);
        when(documents.buildSummary("Body")).thenReturn("Body");
        when(documents.save(any(DocumentWiki.class))).thenAnswer(invocation -> {
            ((DocumentWiki) invocation.getArgument(0)).setId(99L);
            return true;
        });
        AgentWikiDocumentWriter writer = new AgentWikiDocumentWriter(users, spaces, documents, cache);
        assertEquals("99", writer.create(7L, "12", "Title", "Body"));
        ArgumentCaptor<DocumentWiki> captured = ArgumentCaptor.forClass(DocumentWiki.class);
        verify(documents).save(captured.capture());
        assertEquals(7L, captured.getValue().getUserId());
        assertEquals(12L, captured.getValue().getSpaceId());
        assertEquals("markdown", captured.getValue().getContentFormat());
        verify(cache).clearSpace(12L);
    }

    @Test void refusedSpaceCannotBeWritten() {
        UserService users = mock(UserService.class);
        WikiSpaceService spaces = mock(WikiSpaceService.class);
        DocumentWikiService documents = mock(DocumentWikiService.class);
        User user = new User(); user.setId(7L);
        when(users.getById(7L)).thenReturn(user);
        when(spaces.requireEditableSpace(12L, user)).thenThrow(new IllegalArgumentException("No edit permission"));
        AgentWikiDocumentWriter writer = new AgentWikiDocumentWriter(users, spaces, documents, mock(WikiCacheManager.class));
        assertThrows(IllegalArgumentException.class, () -> writer.create(7L, "12", "Title", "Body"));
        verify(documents, never()).save(any(DocumentWiki.class));
    }
}

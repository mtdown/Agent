package com.et.cloud.controller;

import com.et.cloud.mapper.AgentConversationMapper;
import com.et.cloud.mapper.AgentMessageMapper;
import com.et.cloud.model.entity.AgentConversation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConversationStoreTest {
    @Test void newConversationWritesUnicodeTitleExplicitly() {
        AgentConversationMapper conversations = mock(AgentConversationMapper.class);
        when(conversations.insert(any(AgentConversation.class))).thenReturn(1);
        new AgentConversationStore(mock(DataSource.class), conversations, mock(AgentMessageMapper.class)).create(7L);
        ArgumentCaptor<AgentConversation> row = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversations).insert(row.capture());
        assertTrue(row.getValue().getId() > 0);
        assertTrue(row.getValue().getOwnerUserId() == 7L);
        assertTrue("新对话".equals(row.getValue().getTitle()));
    }

    @Test void renameIsOwnerScopedAndTrimmed() {
        AgentConversationMapper conversations = mock(AgentConversationMapper.class);
        when(conversations.renameOwned(7L, 99L, "我的标题")).thenReturn(1);
        new AgentConversationStore(mock(DataSource.class), conversations, mock(AgentMessageMapper.class))
                .rename(7L, 99L, "  我的标题  ");
        verify(conversations).renameOwned(7L, 99L, "我的标题");
    }

    @Test void renameRejectsBlankOrUnauthorizedTitle() {
        AgentConversationStore store = new AgentConversationStore(mock(DataSource.class),
                mock(AgentConversationMapper.class), mock(AgentMessageMapper.class));
        assertThrows(IllegalArgumentException.class, () -> store.rename(7L, 99L, " "));
        assertThrows(IllegalArgumentException.class, () -> store.rename(7L, 99L, "a".repeat(161)));
        assertThrows(IllegalArgumentException.class, () -> store.rename(7L, 99L, "标题"));
    }
    @Test void ownerCheckPreventsReadingMessages() {
        AgentConversationMapper conversations = mock(AgentConversationMapper.class);
        AgentMessageMapper messages = mock(AgentMessageMapper.class);
        AgentConversationStore store = new AgentConversationStore(mock(DataSource.class), conversations, messages);
        assertThrows(IllegalArgumentException.class, () -> store.messages(7L, 99L));
        verify(conversations).countOwned(7L, 99L);
        verifyNoInteractions(messages);
    }
}

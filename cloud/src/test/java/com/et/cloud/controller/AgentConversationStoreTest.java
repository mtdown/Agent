package com.et.cloud.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConversationStoreTest {
    @Test void newConversationWritesUnicodeTitleExplicitly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new AgentConversationStore(jdbc).create(7L);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), anyLong(), eq(7L), eq("新对话"));
        assertTrue(sql.getValue().contains("title"));
    }

    @Test void renameIsOwnerScopedAndTrimmed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), anyLong(), anyLong())).thenReturn(1);
        new AgentConversationStore(jdbc).rename(7L, 99L, "  我的标题  ");
        verify(jdbc).update(contains("ownerUserId = ?"), eq("我的标题"), eq(99L), eq(7L));
    }

    @Test void renameRejectsBlankOrUnauthorizedTitle() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AgentConversationStore store = new AgentConversationStore(jdbc);
        assertThrows(IllegalArgumentException.class, () -> store.rename(7L, 99L, " "));
        assertThrows(IllegalArgumentException.class, () -> store.rename(7L, 99L, "a".repeat(161)));
        assertThrows(IllegalArgumentException.class, () -> store.rename(7L, 99L, "标题"));
    }
    @Test void ownerCheckPreventsReadingMessages() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyLong(), anyLong())).thenReturn(0);
        AgentConversationStore store = new AgentConversationStore(jdbc);
        assertThrows(IllegalArgumentException.class, () -> store.messages(7L, 99L));
        verify(jdbc, never()).queryForList(contains("agent_message"), anyLong());
    }
}

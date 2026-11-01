package com.et.cloud.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentWikiAnswerSkillTest {
    @Test void builtInIsDiscoverableBeforeUserLimitAndVersionIsValidatedPerUser() throws Exception {
        AgentLibraryStore store = new AgentLibraryStore(mock(DataSource.class), mock(PlatformTransactionManager.class),
                new AgentSkillDefinitionValidator(), new AgentCodeVersionValidator());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(store, "jdbc", jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            long user = ((Number) ((Object[]) invocation.getArgument(2))[0]).longValue();
            List<AgentLibraryStore.SkillItem> items = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                AgentLibraryStore.SkillItem item = new AgentLibraryStore.SkillItem();
                item.id = Long.toString(user * 1000 + i + 1); item.name = "user" + user;
                item.summary = "private"; item.version = 1; items.add(item);
            }
            return items;
        });
        for (long user : new long[]{101, 202}) {
            List<AgentLibraryStore.SkillItem> available = store.listAvailable(user);
            assertEquals("builtin:wiki-write", available.get(0).id);
            assertEquals("builtin:wiki-answer", available.get(1).id);
            assertEquals("builtin:wiki-answer", store.listSkills(user).get(1).id);
            assertTrue(new AgentSkillCatalog(store).forUser(user).contains("builtin:wiki-answer@1"));
            AgentLibraryStore.SkillVersion version = store.enabledSkillVersion(user, "builtin:wiki-answer", 1);
            assertEquals("builtin:wiki-answer", version.id);
            assertEquals(version.definitionJson, store.skillVersion(user, version.id, 1).definitionJson);
            JsonNode definition = new AgentSkillDefinitionValidator().validate(version.definitionJson, (a,b,c) -> false);
            String body = definition.toString();
            assertTrue(body.contains("证据")); assertTrue(body.contains("引用")); assertTrue(body.contains("不足"));
            for (JsonNode step : definition.path("steps")) assertEquals("response", step.path("type").asText());
            assertFalse(body.contains("user101")); assertFalse(body.contains("user202"));
            assertThrows(IllegalArgumentException.class, () -> store.skillVersion(user, version.id, 2));
            assertThrows(IllegalArgumentException.class, () -> store.disableSkill(user, version.id));
        }
        verify(jdbc, atLeastOnce()).query(anyString(), any(RowMapper.class), eq(101L), eq(101L));
        verify(jdbc, atLeastOnce()).query(anyString(), any(RowMapper.class), eq(202L), eq(202L));
    }
}

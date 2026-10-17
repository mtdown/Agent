package com.et.cloud.controller;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSkillCatalogTest {
    @Test
    void includesOnlyBriefConditionsFromEnabledUserCatalog() {
        AgentLibraryStore store = mock(AgentLibraryStore.class);
        AgentLibraryStore.SkillItem item = new AgentLibraryStore.SkillItem();
        item.id = "9007199254740993";
        item.version = 2;
        item.name = "Example";
        item.summary = "Does useful work";
        AgentLibraryStore.SkillVersion version = new AgentLibraryStore.SkillVersion();
        version.definitionJson = "{\"when_to_use\":\"matching question\",\"when_not_to_use\":\"other question\","
                + "\"steps\":[{\"type\":\"response\",\"template\":\"private full step\"}]}";
        when(store.listAvailable(7L)).thenReturn(Collections.singletonList(item));
        when(store.skillVersion(7L, item.id, 2)).thenReturn(version);

        String catalog = new AgentSkillCatalog(store).forUser(7L);
        assertTrue(catalog.contains("9007199254740993@2"));
        assertTrue(catalog.contains("matching question"));
        assertFalse(catalog.contains("private full step"));
    }
}

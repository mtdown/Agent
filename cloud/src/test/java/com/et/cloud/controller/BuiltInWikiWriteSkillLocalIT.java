package com.et.cloud.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
class BuiltInWikiWriteSkillLocalIT {
    private static final String ID = "builtin:wiki-write";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private DataSource dataSource;
    @Autowired private AgentLibraryStore store;
    @Autowired private AgentSkillCatalog catalog;
    @Autowired private AgentSkillDefinitionValidator validator;

    @Test void everyUserCanFindAndReadTheBuiltInSkill() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:mysql://localhost:3307/Cloud?"));
        }
        for (long userId : new long[]{101L, 202L}) {
            assertTrue(store.listAvailable(userId).stream().anyMatch(item -> ID.equals(item.id)));
            assertTrue(store.listSkills(userId).stream().anyMatch(item -> ID.equals(item.id)));
            assertTrue(catalog.forUser(userId).contains(ID + "@1"));
            AgentLibraryStore.SkillVersion version = store.enabledSkillVersion(userId, ID, 1);
            assertEquals(ID, version.id);
            JsonNode definition = JSON.readTree(version.definitionJson.getBytes(StandardCharsets.UTF_8));
            assertEquals("文档写入流程", definition.path("name").asText());
            assertDoesNotThrow(() -> validator.validate(version.definitionJson, (id, number, entrypoint) -> false));
            assertEquals(version.definitionJson, store.skillVersion(userId, ID, 1).definitionJson);
        }
    }

    @Test void usersCannotMutateTheReservedSkill() {
        assertThrows(IllegalArgumentException.class, () -> store.disableSkill(101L, ID));
        assertThrows(IllegalArgumentException.class, () -> store.reviseSkill(101L, ID, "{}"));
        assertThrows(IllegalArgumentException.class, () -> store.shareSkill(101L, ID, 202L));
    }
}

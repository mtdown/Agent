package com.et.cloud.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLibraryStoreIT {
    private static final long OWNER = -71L;
    private static final long OTHER = -72L;
    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";
    private static final String OUTPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    @Test
    void createsResponseOnlySkillAsPrivateDraftWithoutCodeValidation() throws Exception {
        DataSource database = localDatabase();
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(database);
        new TransactionTemplate(transactions).execute(status -> {
            status.setRollbackOnly();
            AgentLibraryStore store = new AgentLibraryStore(database, transactions,
                    new AgentSkillDefinitionValidator(), new AgentCodeVersionValidator());
            String definition = "{\"name\":\"Greeting\",\"summary\":\"Replies politely\","
                    + "\"when_to_use\":\"Asked to greet\",\"when_not_to_use\":\"Otherwise\","
                    + "\"parameters\":[],\"steps\":[{\"type\":\"response\",\"template\":\"Hello\"}],"
                    + "\"expected_results\":[\"Hello\"],\"failure_policy\":\"stop\","
                    + "\"examples\":[{\"input\":{},\"expected\":\"Hello\"}]}";
            String id = store.createSkill(OWNER, definition);
            assertTrue(store.listSkills(OWNER).stream().anyMatch(item -> item.id.equals(id) && "DRAFT".equals(item.state)));
            assertFalse(store.listAvailable(OWNER).stream().anyMatch(item -> item.id.equals(id)));
            assertThrows(IllegalArgumentException.class, () -> store.skillVersion(OTHER, id, 1));
            return null;
        });
    }

    @Test
    void privateDraftAndFixedVersionsRemainOwnerScoped() throws Exception {
        DataSource database = localDatabase();
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(database);
        TransactionTemplate outer = new TransactionTemplate(transactions);
        outer.execute(status -> {
            status.setRollbackOnly();
            AgentLibraryStore store = new AgentLibraryStore(database, transactions,
                    new AgentSkillDefinitionValidator(), new AgentCodeVersionValidator());
            String codeId = store.createCode(OWNER, "Example", "Returns a value", "PYTHON", "run",
                    "def run(inputs, resources):\n    return {}", INPUT_SCHEMA, OUTPUT_SCHEMA);
            assertEquals(1, store.codeVersion(OWNER, codeId, 1).version);
            assertThrows(IllegalArgumentException.class, () -> store.codeVersion(OTHER, codeId, 1));
            assertThrows(IllegalArgumentException.class, () -> store.createSkill(OWNER, skill(codeId, 1)));
            store.markCodeValidated(codeId, 1, true);

            String skillId = store.createSkill(OWNER, skill(codeId, 1));
            assertFalse(store.listAvailable(OWNER).stream().anyMatch(item -> item.id.equals(skillId)));
            assertThrows(IllegalArgumentException.class, () -> store.enabledSkillVersion(OWNER, skillId, 1));
            assertThrows(IllegalArgumentException.class, () -> store.skillVersion(OTHER, skillId, 1));
            assertThrows(IllegalArgumentException.class, () -> store.enableSkill(OTHER, skillId, 1));
            store.enableSkill(OWNER, skillId, 1);
            assertTrue(store.listAvailable(OWNER).stream().anyMatch(item -> item.id.equals(skillId)));

            store.shareCode(OWNER, codeId, OTHER);
            store.shareSkill(OWNER, skillId, OTHER);
            assertEquals(1, store.skillVersion(OTHER, skillId, 1).version);
            assertEquals(1, store.enabledSkillVersion(OTHER, skillId, 1).version);
            assertThrows(IllegalArgumentException.class, () -> store.enabledSkillVersion(OTHER, skillId, 2));
            assertThrows(IllegalArgumentException.class, () -> store.reviseSkill(OTHER, skillId, skill(codeId, 1)));

            int next = store.publishCode(OWNER, codeId, "PYTHON", "run", "def run(inputs, resources):\n    return {'new': True}",
                    INPUT_SCHEMA, OUTPUT_SCHEMA);
            assertEquals(2, next);
            assertTrue(store.skillVersion(OWNER, skillId, 1).definitionJson.contains("\"code_version\":1"));
            assertEquals("def run(inputs, resources):\n    return {}", store.codeVersion(OWNER, codeId, 1).sourceCode);
            return null;
        });
    }

    private static DataSource localDatabase() throws Exception {
        ((Logger) LoggerFactory.getLogger("org.springframework.boot.env.OriginTrackedYamlLoader")).setLevel(Level.WARN);
        PropertySource<?> local = new YamlPropertySourceLoader()
                .load("local", new ClassPathResource("application-local.yml")).get(0);
        String url = String.valueOf(local.getProperty("spring.datasource.url"));
        if (!url.startsWith("jdbc:mysql://localhost:3307/Cloud?"))
            throw new IllegalStateException("Integration test may only use the local development database");
        return new DriverManagerDataSource(url, String.valueOf(local.getProperty("spring.datasource.username")),
                String.valueOf(local.getProperty("spring.datasource.password")));
    }

    private static String skill(String codeId, int version) {
        return "{\"name\":\"Example\",\"summary\":\"Runs code\",\"when_to_use\":\"example\","
                + "\"when_not_to_use\":\"otherwise\",\"parameters\":[],\"steps\":[{\"type\":\"code\","
                + "\"code_item_id\":\"" + codeId + "\",\"code_version\":" + version + ",\"entrypoint\":\"run\","
                + "\"args\":{}}],\"expected_results\":[\"JSON\"],\"failure_policy\":\"stop\","
                + "\"examples\":[{\"input\":{},\"expected\":{}}]}";
    }
}

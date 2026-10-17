package com.et.cloud.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.et.cloud.mapper.AgentConversationMapper;
import com.et.cloud.mapper.AgentMessageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentLibrarySchemaIT {
    @Test
    void startupCreatesLibraryTablesInDevelopmentDatabase() throws Exception {
        ((Logger) LoggerFactory.getLogger("org.springframework.boot.env.OriginTrackedYamlLoader"))
                .setLevel(Level.WARN);
        PropertySource<?> local = new YamlPropertySourceLoader()
                .load("local", new ClassPathResource("application-local.yml")).get(0);
        String url = (String) local.getProperty("spring.datasource.url");
        assertTrue(url != null && url.startsWith("jdbc:mysql://localhost:3307/Cloud?"),
                "This integration test may only use the local development database");
        String username = String.valueOf(local.getProperty("spring.datasource.username"));
        String password = String.valueOf(local.getProperty("spring.datasource.password"));
        DataSource dataSource = new DriverManagerDataSource(url, username, password);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer conversationsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM agent_conversation", Integer.class);
        String oldTableDdl = jdbc.queryForObject("SHOW CREATE TABLE agent_conversation",
                (rs, rowNum) -> rs.getString(2));

        AgentConversationStore store = new AgentConversationStore(dataSource,
                mock(AgentConversationMapper.class), mock(AgentMessageMapper.class));
        store.initializeTables();
        store.initializeTables();

        List<String> names = jdbc.queryForList("SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() AND table_name IN " +
                "('agent_skill_item', 'agent_skill_version', 'agent_skill_share', " +
                "'agent_code_item', 'agent_code_version', 'agent_code_share', 'agent_code_validation', " +
                "'agent_skill_execution', 'agent_skill_step_execution')", String.class);
        assertEquals(9, names.size());
        assertEquals(conversationsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM agent_conversation", Integer.class));
        assertEquals(oldTableDdl, jdbc.queryForObject("SHOW CREATE TABLE agent_conversation",
                (rs, rowNum) -> rs.getString(2)));
    }
}

package com.et.cloud.controller;

import com.et.cloud.mapper.AgentConversationMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/** Startup schema preparation is separate from mapped conversation persistence. */
final class AgentConversationInitializer {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final AgentConversationMapper conversations;

    AgentConversationInitializer(DataSource dataSource, AgentConversationMapper conversations) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.conversations = conversations;
    }

    void initialize() {
        ResourceDatabasePopulator conversationsScript = new ResourceDatabasePopulator(
                new ClassPathResource("agent-conversation.sql"));
        conversationsScript.setSqlScriptEncoding("UTF-8");
        conversationsScript.execute(Objects.requireNonNull(dataSource));
        ensureChoiceColumn("interactionJson", "mediumtext NULL");
        ensureChoiceColumn("choiceConsumed", "tinyint NOT NULL DEFAULT 0");
        ResourceDatabasePopulator libraryScript = new ResourceDatabasePopulator(
                new ClassPathResource("agent-skill-library.sql"));
        libraryScript.setSqlScriptEncoding("UTF-8");
        libraryScript.execute(dataSource);
        // Only repair the exact mojibake placeholder, never user-chosen titles.
        conversations.repairLegacyTitle("新对话", "E98F82E69DBFEE87AEE79287EFBFBD");
    }

    void ensureChoiceColumn(String column, String definition) {
        try (Connection connection = dataSource.getConnection();
             ResultSet fields = connection.getMetaData().getColumns(null, null, "agent_message", column)) {
            if (!fields.next()) jdbc.execute("ALTER TABLE agent_message ADD COLUMN " + column + " " + definition);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not prepare Agent choice storage", e);
        }
    }
}

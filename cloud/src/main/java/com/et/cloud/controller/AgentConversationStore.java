package com.et.cloud.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** All conversation lookups are owner-scoped, including updates and deletes. */
@Service
public class AgentConversationStore {
    private final JdbcTemplate jdbc;
    public AgentConversationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void initializeTables() {
        new ResourceDatabasePopulator(new ClassPathResource("agent-conversation.sql"))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
        // The original Windows import stored the UTF-8 default title as this exact mojibake value.
        // Only repair that placeholder, so titles explicitly chosen by users remain untouched.
        jdbc.update("UPDATE agent_conversation c SET title = COALESCE((SELECT LEFT(m.content, 80) " +
                        "FROM agent_message m WHERE m.conversationId = c.id AND m.role = 'user' " +
                        "ORDER BY m.id LIMIT 1), ?) WHERE HEX(c.title) = ?",
                "新对话", "E98F82E69DBFEE87AEE79287EFBFBD");
    }

    public long create(long ownerId) {
        long id = IdWorker.getId();
        jdbc.update("INSERT INTO agent_conversation (id, ownerUserId, title) VALUES (?, ?, ?)", id, ownerId, "新对话");
        return id;
    }

    public List<Map<String, Object>> list(long ownerId) {
        return jdbc.queryForList("SELECT CAST(id AS CHAR) id, title, createTime, updateTime FROM agent_conversation " +
                "WHERE ownerUserId = ? AND isDelete = 0 ORDER BY updateTime DESC, id DESC LIMIT 100", ownerId);
    }

    public List<Map<String, Object>> messages(long ownerId, long conversationId) {
        requireOwner(ownerId, conversationId);
        return jdbc.queryForList("SELECT CAST(id AS CHAR) id, CAST(turnId AS CHAR) turnId, role, content, status, ragMode, " +
                "spaceIdsJson, citationsJson, statsJson, createTime FROM agent_message WHERE conversationId = ? ORDER BY id", conversationId);
    }

    public void requireOwner(long ownerId, long conversationId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM agent_conversation WHERE id = ? AND ownerUserId = ? AND isDelete = 0",
                Integer.class, conversationId, ownerId);
        if (count == null || count == 0) throw new IllegalArgumentException("Conversation not found");
    }

    public void delete(long ownerId, long conversationId) {
        if (jdbc.update("UPDATE agent_conversation SET isDelete = 1 WHERE id = ? AND ownerUserId = ? AND isDelete = 0 AND busy = 0",
                conversationId, ownerId) != 1) throw new IllegalArgumentException("Conversation not found or busy");
    }

    public void rename(long ownerId, long conversationId, String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty() || normalized.length() > 160) throw new IllegalArgumentException("Invalid title");
        if (jdbc.update("UPDATE agent_conversation SET title = ? WHERE id = ? AND ownerUserId = ? AND isDelete = 0 AND busy = 0",
                normalized, conversationId, ownerId) != 1) throw new IllegalArgumentException("Conversation not found or busy");
    }

    public long begin(long ownerId, long conversationId, String query, String ragMode, String spaceIdsJson) {
        if (jdbc.update("UPDATE agent_conversation SET busy = 1, title = IF(title = '新对话', ?, title) " +
                        "WHERE id = ? AND ownerUserId = ? AND isDelete = 0 AND busy = 0",
                query.length() > 80 ? query.substring(0, 80) : query, conversationId, ownerId) != 1)
            throw new IllegalArgumentException("Conversation not found or busy");
        long turnId = IdWorker.getId();
        try {
            jdbc.update("INSERT INTO agent_message (id, conversationId, turnId, role, content, status, ragMode, spaceIdsJson) " +
                            "VALUES (?, ?, ?, 'user', ?, 'PENDING', ?, ?)", turnId, conversationId, turnId, query, ragMode, spaceIdsJson);
            return turnId;
        } catch (RuntimeException e) {
            release(ownerId, conversationId);
            throw e;
        }
    }

    public List<Map<String, Object>> recentCompleted(long ownerId, long conversationId) {
        requireOwner(ownerId, conversationId);
        return jdbc.queryForList("SELECT role, LEFT(content, 4000) content FROM (" +
                "SELECT m.id, m.role, m.content FROM agent_message m JOIN agent_message u ON u.id = m.turnId " +
                "WHERE m.conversationId = ? AND u.status = 'DONE' AND m.status = 'DONE' " +
                "ORDER BY m.id DESC LIMIT 12) recent ORDER BY id", conversationId);
    }

    public void complete(long ownerId, long conversationId, long turnId, String answer, String citationsJson, String statsJson) {
        requireOwner(ownerId, conversationId);
        try {
            jdbc.update("INSERT INTO agent_message (id, conversationId, turnId, role, content, status, citationsJson, statsJson) " +
                    "VALUES (?, ?, ?, 'assistant', ?, 'DONE', ?, ?)", IdWorker.getId(), conversationId, turnId, answer, citationsJson, statsJson);
            jdbc.update("UPDATE agent_message SET status = 'DONE' WHERE id = ? AND conversationId = ?", turnId, conversationId);
        } finally { release(ownerId, conversationId); }
    }

    public void fail(long ownerId, long conversationId, long turnId) {
        jdbc.update("UPDATE agent_message SET status = 'FAILED' WHERE id = ? AND conversationId = ? AND status = 'PENDING'", turnId, conversationId);
        release(ownerId, conversationId);
    }

    private void release(long ownerId, long conversationId) {
        jdbc.update("UPDATE agent_conversation SET busy = 0 WHERE id = ? AND ownerUserId = ?", conversationId, ownerId);
    }
}

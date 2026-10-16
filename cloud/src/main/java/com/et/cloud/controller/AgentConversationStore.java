package com.et.cloud.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.et.cloud.mapper.AgentConversationMapper;
import com.et.cloud.mapper.AgentMessageMapper;
import com.et.cloud.model.entity.AgentConversation;
import com.et.cloud.model.entity.AgentMessage;
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
    private final DataSource dataSource;
    private final AgentConversationMapper conversations;
    private final AgentMessageMapper messages;

    public AgentConversationStore(DataSource dataSource, AgentConversationMapper conversations,
                                  AgentMessageMapper messages) {
        this.dataSource = dataSource;
        this.conversations = conversations;
        this.messages = messages;
    }

    @PostConstruct
    public void initializeTables() {
        new ResourceDatabasePopulator(new ClassPathResource("agent-conversation.sql"))
                .execute(Objects.requireNonNull(dataSource));
        // The original Windows import stored the UTF-8 default title as this exact mojibake value.
        // Only repair that placeholder, so titles explicitly chosen by users remain untouched.
        conversations.repairLegacyTitle("新对话", "E98F82E69DBFEE87AEE79287EFBFBD");
    }

    public long create(long ownerId) {
        long id = IdWorker.getId();
        AgentConversation conversation = new AgentConversation();
        conversation.setId(id);
        conversation.setOwnerUserId(ownerId);
        conversation.setTitle("新对话");
        conversation.setBusy(0);
        conversation.setIsDelete(0);
        if (conversations.insert(conversation) != 1) throw new IllegalStateException("Could not create conversation");
        return id;
    }

    public List<Map<String, Object>> list(long ownerId) {
        return conversations.listOwned(ownerId);
    }

    public List<Map<String, Object>> messages(long ownerId, long conversationId) {
        requireOwner(ownerId, conversationId);
        return messages.listByConversation(conversationId);
    }

    public void requireOwner(long ownerId, long conversationId) {
        if (conversations.countOwned(ownerId, conversationId) == 0)
            throw new IllegalArgumentException("Conversation not found");
    }

    public void delete(long ownerId, long conversationId) {
        if (conversations.markDeleted(ownerId, conversationId) != 1)
            throw new IllegalArgumentException("Conversation not found or busy");
    }

    public void rename(long ownerId, long conversationId, String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty() || normalized.length() > 160) throw new IllegalArgumentException("Invalid title");
        if (conversations.renameOwned(ownerId, conversationId, normalized) != 1)
            throw new IllegalArgumentException("Conversation not found or busy");
    }

    public long begin(long ownerId, long conversationId, String query, String ragMode, String spaceIdsJson) {
        if (conversations.claimForTurn(ownerId, conversationId,
                query.length() > 80 ? query.substring(0, 80) : query) != 1)
            throw new IllegalArgumentException("Conversation not found or busy");
        long turnId = IdWorker.getId();
        try {
            AgentMessage message = new AgentMessage();
            message.setId(turnId);
            message.setConversationId(conversationId);
            message.setTurnId(turnId);
            message.setRole("user");
            message.setContent(query);
            message.setStatus("PENDING");
            message.setRagMode(ragMode);
            message.setSpaceIdsJson(spaceIdsJson);
            if (messages.insert(message) != 1) throw new IllegalStateException("Could not save question");
            return turnId;
        } catch (RuntimeException e) {
            release(ownerId, conversationId);
            throw e;
        }
    }

    public List<Map<String, Object>> recentCompleted(long ownerId, long conversationId) {
        requireOwner(ownerId, conversationId);
        return messages.recentCompleted(conversationId);
    }

    public void complete(long ownerId, long conversationId, long turnId, String answer, String citationsJson, String statsJson) {
        requireOwner(ownerId, conversationId);
        try {
            AgentMessage message = new AgentMessage();
            message.setId(IdWorker.getId());
            message.setConversationId(conversationId);
            message.setTurnId(turnId);
            message.setRole("assistant");
            message.setContent(answer);
            message.setStatus("DONE");
            message.setCitationsJson(citationsJson);
            message.setStatsJson(statsJson);
            if (messages.insert(message) != 1) throw new IllegalStateException("Could not save answer");
            messages.markDone(conversationId, turnId);
        } finally { release(ownerId, conversationId); }
    }

    public void fail(long ownerId, long conversationId, long turnId) {
        messages.markFailed(conversationId, turnId);
        release(ownerId, conversationId);
    }

    private void release(long ownerId, long conversationId) {
        conversations.release(ownerId, conversationId);
    }
}

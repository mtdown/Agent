package com.et.cloud.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.et.cloud.mapper.AgentConversationMapper;
import com.et.cloud.mapper.AgentMessageMapper;
import com.et.cloud.model.entity.AgentConversation;
import com.et.cloud.model.entity.AgentMessage;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** All conversation lookups are owner-scoped, including updates and deletes. */
@Service
public class AgentConversationStore {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentWikiChoiceService choices;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.et.cloud.service.UserService users;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final AgentConversationMapper conversations;
    private final AgentMessageMapper messages;

    public AgentConversationStore(DataSource dataSource, AgentConversationMapper conversations,
                                  AgentMessageMapper messages) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.conversations = conversations;
        this.messages = messages;
    }

    @PostConstruct
    public void initializeTables() {
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
        // The original Windows import stored the UTF-8 default title as this exact mojibake value.
        // Only repair that placeholder, so titles explicitly chosen by users remain untouched.
        conversations.repairLegacyTitle("新对话", "E98F82E69DBFEE87AEE79287EFBFBD");
    }

    private void ensureChoiceColumn(String column, String definition) {
        try (Connection connection = dataSource.getConnection();
             ResultSet fields = connection.getMetaData().getColumns(null, null, "agent_message", column)) {
            if (!fields.next()) jdbc.execute("ALTER TABLE agent_message ADD COLUMN " + column + " " + definition);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not prepare Agent choice storage", e);
        }
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
        return projectRows(ownerId, messages.listByConversation(conversationId), true);
    }

    List<Map<String, Object>> messagesRaw(long ownerId, long conversationId) {
        requireOwner(ownerId, conversationId);
        return messages.listByConversation(conversationId);
    }

    private List<Map<String,Object>> projectRows(long ownerId, List<Map<String,Object>> originals, boolean includeInteraction) {
        List<Map<String,Object>> result=new java.util.ArrayList<>();
        List<String> inputs=new java.util.ArrayList<>(); List<Boolean> consumed=new java.util.ArrayList<>();
        for (Map<String,Object> original: originals) {
            Map<String,Object> row=new java.util.LinkedHashMap<>(original); result.add(row);
            AgentPersonalFileReferences.project(row);
            Object raw=row.remove("interactionJson"), used=row.remove("choiceConsumed");
            if(raw!=null){inputs.add(String.valueOf(raw)); consumed.add(used instanceof Number && ((Number)used).intValue()!=0);}
        }
        List<Map<String,Object>> displays = null;
        if (choices != null && !inputs.isEmpty()) {
            com.et.cloud.model.entity.User owner=users == null ? null : users.getById(ownerId);
            if(owner==null){owner=new com.et.cloud.model.entity.User(); owner.setId(ownerId);}
            displays=choices.currentDisplays(owner,inputs,consumed);
        }
        int index=0;
        for(int i=0;i<originals.size();i++) {
            Object raw=originals.get(i).get("interactionJson"); if(raw==null) continue;
            Map<String,Object> row=result.get(i);
            Map<String,Object> display=displays == null ? AgentWikiChoiceService.safeDisplay(String.valueOf(raw),consumed.get(index)) : displays.get(index);
            if(includeInteraction) row.put("interaction",display);
            if(choices!=null && !"destination_choice".equals(display.get("kind"))) {
                try {
                    com.fasterxml.jackson.databind.JsonNode state=new com.fasterxml.jackson.databind.ObjectMapper().readTree(String.valueOf(raw));
                    row.put("content",AgentWikiChoiceService.answerFor(display,state.path("unknown").asInt(),state.path("failed").asInt()));
                } catch(java.io.IOException e){throw new IllegalStateException("Invalid stored Agent interaction",e);}
            }
            index++;
        }
        return result;
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
        return projectRows(ownerId, messages.recentCompleted(conversationId), false);
    }

    public void complete(long ownerId, long conversationId, long turnId, String answer, String citationsJson, String statsJson) {
        complete(ownerId, conversationId, turnId, answer, citationsJson, statsJson, null);
    }

    public void complete(long ownerId, long conversationId, long turnId, String answer, String citationsJson,
                         String statsJson, String interactionJson) {
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
            message.setInteractionJson(interactionJson);
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

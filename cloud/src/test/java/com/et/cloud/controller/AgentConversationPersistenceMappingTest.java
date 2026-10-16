package com.et.cloud.controller;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConversationPersistenceMappingTest {

    @Test
    void conversationStoreUsesMappedEntities() throws Exception {
        Class<?> conversation = Class.forName("com.et.cloud.model.entity.AgentConversation");
        Class<?> message = Class.forName("com.et.cloud.model.entity.AgentMessage");
        Class<?> conversationMapper = Class.forName("com.et.cloud.mapper.AgentConversationMapper");
        Class<?> messageMapper = Class.forName("com.et.cloud.mapper.AgentMessageMapper");

        assertEquals("agent_conversation", conversation.getAnnotation(TableName.class).value());
        assertEquals("agent_message", message.getAnnotation(TableName.class).value());
        assertTrue(BaseMapper.class.isAssignableFrom(conversationMapper));
        assertTrue(BaseMapper.class.isAssignableFrom(messageMapper));

        Class<?>[] dependencies = Arrays.stream(AgentConversationStore.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getType).toArray(Class<?>[]::new);
        assertTrue(Arrays.asList(dependencies).contains(conversationMapper));
        assertTrue(Arrays.asList(dependencies).contains(messageMapper));
        assertFalse(Arrays.stream(dependencies).anyMatch(type -> type.getName().equals("org.springframework.jdbc.core.JdbcTemplate")));
    }
}

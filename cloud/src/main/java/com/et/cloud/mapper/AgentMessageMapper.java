package com.et.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.et.cloud.model.entity.AgentMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface AgentMessageMapper extends BaseMapper<AgentMessage> {
    @Select("SELECT CAST(id AS CHAR) id, CAST(turnId AS CHAR) turnId, role, content, status, ragMode, " +
            "spaceIdsJson, citationsJson, statsJson, interactionJson, choiceConsumed, createTime FROM agent_message " +
            "WHERE conversationId = #{conversationId} ORDER BY id")
    List<Map<String, Object>> listByConversation(@Param("conversationId") long conversationId);

    @Select("SELECT role, LEFT(content, 4000) content FROM (" +
            "SELECT m.id, m.role, m.content FROM agent_message m JOIN agent_message u ON u.id = m.turnId " +
            "WHERE m.conversationId = #{conversationId} AND u.status = 'DONE' AND m.status = 'DONE' " +
            "ORDER BY m.id DESC LIMIT 12) recent ORDER BY id")
    List<Map<String, Object>> recentCompleted(@Param("conversationId") long conversationId);

    @Update("UPDATE agent_message SET status = 'DONE' WHERE id = #{turnId} AND conversationId = #{conversationId}")
    int markDone(@Param("conversationId") long conversationId, @Param("turnId") long turnId);

    @Update("UPDATE agent_message SET status = 'FAILED' " +
            "WHERE id = #{turnId} AND conversationId = #{conversationId} AND status = 'PENDING'")
    int markFailed(@Param("conversationId") long conversationId, @Param("turnId") long turnId);
}

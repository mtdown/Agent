package com.et.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.et.cloud.model.entity.AgentConversation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface AgentConversationMapper extends BaseMapper<AgentConversation> {
    @Update("UPDATE agent_conversation c SET title = COALESCE((SELECT LEFT(m.content, 80) " +
            "FROM agent_message m WHERE m.conversationId = c.id AND m.role = 'user' " +
            "ORDER BY m.id LIMIT 1), #{fallbackTitle}) WHERE HEX(c.title) = #{legacyTitleHex}")
    int repairLegacyTitle(@Param("fallbackTitle") String fallbackTitle,
                          @Param("legacyTitleHex") String legacyTitleHex);

    @Select("SELECT CAST(id AS CHAR) id, title, createTime, updateTime FROM agent_conversation " +
            "WHERE ownerUserId = #{ownerId} AND isDelete = 0 ORDER BY updateTime DESC, id DESC LIMIT 100")
    List<Map<String, Object>> listOwned(@Param("ownerId") long ownerId);

    @Select("SELECT COUNT(*) FROM agent_conversation " +
            "WHERE id = #{conversationId} AND ownerUserId = #{ownerId} AND isDelete = 0")
    int countOwned(@Param("ownerId") long ownerId, @Param("conversationId") long conversationId);

    @Update("UPDATE agent_conversation SET isDelete = 1 " +
            "WHERE id = #{conversationId} AND ownerUserId = #{ownerId} AND isDelete = 0 AND busy = 0")
    int markDeleted(@Param("ownerId") long ownerId, @Param("conversationId") long conversationId);

    @Update("UPDATE agent_conversation SET title = #{title} " +
            "WHERE id = #{conversationId} AND ownerUserId = #{ownerId} AND isDelete = 0 AND busy = 0")
    int renameOwned(@Param("ownerId") long ownerId, @Param("conversationId") long conversationId,
                    @Param("title") String title);

    @Update("UPDATE agent_conversation SET busy = 1, title = IF(title = '新对话', #{title}, title) " +
            "WHERE id = #{conversationId} AND ownerUserId = #{ownerId} AND isDelete = 0 AND busy = 0")
    int claimForTurn(@Param("ownerId") long ownerId, @Param("conversationId") long conversationId,
                     @Param("title") String title);

    @Update("UPDATE agent_conversation SET busy = 0 WHERE id = #{conversationId} AND ownerUserId = #{ownerId}")
    int release(@Param("ownerId") long ownerId, @Param("conversationId") long conversationId);
}

package com.et.cloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("agent_message")
public class AgentMessage {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long conversationId;
    private Long turnId;
    private String role;
    private String content;
    private String status;
    private String ragMode;
    private String spaceIdsJson;
    private String citationsJson;
    private String statsJson;
    private Date createTime;
}

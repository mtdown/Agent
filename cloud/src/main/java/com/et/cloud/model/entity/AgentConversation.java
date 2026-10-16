package com.et.cloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("agent_conversation")
public class AgentConversation {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long ownerUserId;
    private String title;
    private Integer busy;
    private Date createTime;
    private Date updateTime;
    private Integer isDelete;
}

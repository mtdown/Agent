package com.et.cloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName(value = "wiki_space")
@Data
public class WikiSpace {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 0-personal, 1-team, 2-public.
     */
    private Integer type;

    private String name;

    private Long ownerUserId;

    private Date createTime;

    private Date updateTime;

    @TableField
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

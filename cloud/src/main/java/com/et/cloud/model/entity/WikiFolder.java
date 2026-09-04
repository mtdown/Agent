package com.et.cloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName(value = "wiki_folder")
@Data
public class WikiFolder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long spaceId;

    private Long parentId;

    private String name;

    private Date deleteTime;

    private Long deleteBy;

    private Date createTime;

    private Date editTime;

    private Date updateTime;

    @TableField
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

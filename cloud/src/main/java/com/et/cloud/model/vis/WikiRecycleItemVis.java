package com.et.cloud.model.vis;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class WikiRecycleItemVis implements Serializable {

    private String itemType;

    private Long itemId;

    private Long spaceId;

    private Long parentId;

    private String title;

    private Date deleteTime;

    private Long deleteBy;

    private UserVis deleteUser;

    private static final long serialVersionUID = 1L;
}

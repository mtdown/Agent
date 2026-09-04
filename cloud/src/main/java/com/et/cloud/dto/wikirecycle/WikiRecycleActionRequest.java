package com.et.cloud.dto.wikirecycle;

import lombok.Data;

import java.io.Serializable;

@Data
public class WikiRecycleActionRequest implements Serializable {

    private Long spaceId;

    private Long itemId;

    private String itemType;

    private Boolean confirm;

    private static final long serialVersionUID = 1L;
}

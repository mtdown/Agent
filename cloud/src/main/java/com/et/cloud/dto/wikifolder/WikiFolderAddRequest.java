package com.et.cloud.dto.wikifolder;

import lombok.Data;

import java.io.Serializable;

@Data
public class WikiFolderAddRequest implements Serializable {

    private Long spaceId;

    private Long parentId;

    private String name;

    private static final long serialVersionUID = 1L;
}

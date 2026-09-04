package com.et.cloud.dto.wikifolder;

import lombok.Data;

import java.io.Serializable;

@Data
public class WikiFolderMoveRequest implements Serializable {

    private Long id;

    private Long parentId;

    private static final long serialVersionUID = 1L;
}

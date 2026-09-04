package com.et.cloud.dto.documentWiki;

import lombok.Data;

import java.io.Serializable;

@Data
public class DocumentWikiMoveRequest implements Serializable {

    private Long id;

    private Long targetSpaceId;

    private Long targetFolderId;

    private static final long serialVersionUID = 1L;
}

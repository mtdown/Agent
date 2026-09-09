package com.et.cloud.dto.wikispace;

import lombok.Data;

import java.io.Serializable;

@Data
public class WikiSpaceRenameRequest implements Serializable {

    private Long id;

    private String name;

    private static final long serialVersionUID = 1L;
}

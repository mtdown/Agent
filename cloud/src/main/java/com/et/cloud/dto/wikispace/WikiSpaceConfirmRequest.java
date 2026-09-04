package com.et.cloud.dto.wikispace;

import lombok.Data;

import java.io.Serializable;

@Data
public class WikiSpaceConfirmRequest implements Serializable {

    private Long id;

    private Boolean confirm;

    private static final long serialVersionUID = 1L;
}

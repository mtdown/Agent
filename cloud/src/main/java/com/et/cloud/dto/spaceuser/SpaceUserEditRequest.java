package com.et.cloud.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceUserEditRequest implements Serializable {

    private Long id;

    private String spaceRole;

    private static final long serialVersionUID = 1L;
}
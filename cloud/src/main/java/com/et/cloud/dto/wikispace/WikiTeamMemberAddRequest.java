package com.et.cloud.dto.wikispace;

import lombok.Data;

import java.io.Serializable;

@Data
public class WikiTeamMemberAddRequest implements Serializable {

    private Long spaceId;

    private Long userId;

    private String spaceRole;

    private static final long serialVersionUID = 1L;
}

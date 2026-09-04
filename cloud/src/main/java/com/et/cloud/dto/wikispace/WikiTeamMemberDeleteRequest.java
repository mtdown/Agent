package com.et.cloud.dto.wikispace;

import lombok.Data;

import java.io.Serializable;

@Data
public class WikiTeamMemberDeleteRequest implements Serializable {

    private Long spaceId;

    private Long userId;

    private static final long serialVersionUID = 1L;
}

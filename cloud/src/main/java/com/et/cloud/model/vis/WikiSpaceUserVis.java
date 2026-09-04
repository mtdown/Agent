package com.et.cloud.model.vis;

import com.et.cloud.model.entity.WikiSpaceUser;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

@Data
public class WikiSpaceUserVis implements Serializable {

    private Long id;

    private Long spaceId;

    private Long userId;

    private String spaceRole;

    private Date createTime;

    private Date updateTime;

    private UserVis user;

    private static final long serialVersionUID = 1L;

    public static WikiSpaceUserVis objToVis(WikiSpaceUser wikiSpaceUser) {
        if (wikiSpaceUser == null) {
            return null;
        }
        WikiSpaceUserVis wikiSpaceUserVis = new WikiSpaceUserVis();
        BeanUtils.copyProperties(wikiSpaceUser, wikiSpaceUserVis);
        return wikiSpaceUserVis;
    }
}

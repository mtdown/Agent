package com.et.cloud.model.vis;

import com.et.cloud.model.entity.SpaceUser;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

@Data
public class SpaceUserVis implements Serializable {

    private Long id;

    private Long spaceId;

    private Long userId;

    private String spaceRole;

    private Date createTime;

    private Date updateTime;

    private UserVis user;

    private SpaceVis space;
    private static final long serialVersionUID = 1L;

    public static SpaceUser voToObj(SpaceUserVis spaceUserVis) {
        if (spaceUserVis == null) {
            return null;
        }
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserVis, spaceUser);
        return spaceUser;
    }

    public static SpaceUserVis objToVis(SpaceUser spaceUser) {
        if (spaceUser == null) {
            return null;
        }
        SpaceUserVis spaceUserVis = new SpaceUserVis();
        BeanUtils.copyProperties(spaceUser, spaceUserVis);
        return spaceUserVis;
    }
}

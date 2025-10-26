package com.et.cloud.model.vis;

import com.et.cloud.model.entity.Space;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

@Data
public class SpaceVis implements Serializable {

    private Long id;


    private String spaceName;


    private Integer spaceLevel;


    private Long maxSize;


    private Long maxCount;


    private Long totalSize;


    private Long totalCount;


    private Long userId;


    private Date createTime;


    private Date editTime;


    private Date updateTime;


    private UserVis user;

    private static final long serialVersionUID = 1L;


    public static Space visToObj(SpaceVis spaceVis) {
        if (spaceVis == null) {
            return null;
        }
        Space space = new Space();
        BeanUtils.copyProperties(spaceVis, space);
        return space;
    }


    public static SpaceVis objToVis(Space space) {
        if (space == null) {
            return null;
        }
        SpaceVis spaceVis = new SpaceVis();
        BeanUtils.copyProperties(space, spaceVis);
        return spaceVis;
    }
}
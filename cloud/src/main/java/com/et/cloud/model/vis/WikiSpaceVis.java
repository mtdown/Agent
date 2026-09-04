package com.et.cloud.model.vis;

import com.et.cloud.model.entity.WikiSpace;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

@Data
public class WikiSpaceVis implements Serializable {

    private Long id;

    private Integer type;

    private String name;

    private Long ownerUserId;

    private Date createTime;

    private Date updateTime;

    private Integer isDelete;

    private static final long serialVersionUID = 1L;

    public static WikiSpaceVis objToVis(WikiSpace wikiSpace) {
        if (wikiSpace == null) {
            return null;
        }
        WikiSpaceVis wikiSpaceVis = new WikiSpaceVis();
        BeanUtils.copyProperties(wikiSpace, wikiSpaceVis);
        return wikiSpaceVis;
    }
}

package com.et.cloud.model.vis;

import com.et.cloud.model.entity.WikiFolder;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class WikiFolderVis implements Serializable {

    private Long id;

    private Long spaceId;

    private Long parentId;

    private String name;

    private Date deleteTime;

    private Long deleteBy;

    private Date createTime;

    private Date editTime;

    private Date updateTime;

    private List<WikiFolderVis> children = new ArrayList<>();

    private List<DocumentWikiVis> documents = new ArrayList<>();

    private UserVis deleteUser;

    private static final long serialVersionUID = 1L;

    public static WikiFolderVis objToVis(WikiFolder wikiFolder) {
        if (wikiFolder == null) {
            return null;
        }
        WikiFolderVis wikiFolderVis = new WikiFolderVis();
        BeanUtils.copyProperties(wikiFolder, wikiFolderVis);
        return wikiFolderVis;
    }
}

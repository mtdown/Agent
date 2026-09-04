package com.et.cloud.model.vis;

import cn.hutool.json.JSONUtil;
import com.et.cloud.model.entity.DocumentWiki;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class DocumentWikiVis implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * Document title.
     */
    private String title;

    /**
     * Saved document body.
     */
    private String content;

    /**
     * Short summary.
     */
    private String summary;

    /**
     * Tags.
     */
    private List<String> tags = new ArrayList<>();

    /**
     * Creator user id.
     */
    private Long userId;

    private Long spaceId;

    private Long folderId;

    /**
     * View count.
     */
    private Long viewCount;

    /**
     * Create time.
     */
    private Date createTime;

    /**
     * Edit time.
     */
    private Date editTime;

    /**
     * Update time.
     */
    private Date updateTime;

    private Date deleteTime;

    private Long deleteBy;

    /**
     * Creator user info.
     */
    private UserVis user;

    private static final long serialVersionUID = 1L;

    /**
     * View object to entity.
     */
    public static DocumentWiki VisToObj(DocumentWikiVis documentWikiVis) {
        if (documentWikiVis == null) {
            return null;
        }
        DocumentWiki documentWiki = new DocumentWiki();
        BeanUtils.copyProperties(documentWikiVis, documentWiki);
        documentWiki.setTags(JSONUtil.toJsonStr(documentWikiVis.getTags()));
        return documentWiki;
    }

    /**
     * Entity to view object.
     */
    public static DocumentWikiVis objToVis(DocumentWiki documentWiki) {
        if (documentWiki == null) {
            return null;
        }
        DocumentWikiVis documentWikiVis = new DocumentWikiVis();
        BeanUtils.copyProperties(documentWiki, documentWikiVis);
        if (JSONUtil.isTypeJSON(documentWiki.getTags())) {
            documentWikiVis.setTags(JSONUtil.toList(documentWiki.getTags(), String.class));
        }
        return documentWikiVis;
    }
}

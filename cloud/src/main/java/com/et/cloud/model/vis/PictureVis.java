package com.et.cloud.model.vis;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import com.et.cloud.model.entity.Picture;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class PictureVis implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 图片 url
     */
    private String url;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 分类
     */
    private String category;

    /**
     * 文件体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 用户 id
     */
    private Long userId;

    private Long spaceId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建用户信息
     */
    private UserVis user;

    private static final long serialVersionUID = 1L;

    /**
     * 缩略图地址
     */
    private String thumbnailUrl;

    /**
     * 封装类转对象
     */
    public static Picture VisToObj(PictureVis pictureVis) {
        if (pictureVis == null) {
            return null;
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureVis, picture);
        // 类型不同，需要转换  
        picture.setTags(JSONUtil.toJsonStr(pictureVis.getTags()));
        return picture;
    }

    /**
     * 对象转封装类
     */
    public static PictureVis objToVis(Picture picture) {
        if (picture == null) {
            return null;
        }
        PictureVis pictureVis = new PictureVis();
        BeanUtils.copyProperties(picture, pictureVis);
        // 类型不同，需要转换  
        pictureVis.setTags(JSONUtil.toList(picture.getTags(), String.class));
        return pictureVis;
    }
}

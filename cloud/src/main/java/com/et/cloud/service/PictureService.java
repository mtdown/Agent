package com.et.cloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.et.cloud.dto.picture.PictureQueryRequest;
import com.et.cloud.dto.picture.PictureUploadRequest;
import com.et.cloud.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.vis.PictureVis;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
 * @author origin
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2025-09-28 16:58:07
 */
public interface PictureService extends IService<Picture> {
    /**
     * 上传图片
     *
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVis uploadPicture(MultipartFile multipartFile,
                             PictureUploadRequest pictureUploadRequest,
                             User loginUser);

    /**
     * 获取查询条件
     *
     * @param pictureQueryRequest 查询条件
     * @return 查询条件
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    public PictureVis getPictureVis(Picture picture, HttpServletRequest httpServletRequest);

    public Page<PictureVis> getPictureVisPage(Page<Picture> picturePage, HttpServletRequest httpServletRequest);

    public void vaildPicture(Picture picture);


}

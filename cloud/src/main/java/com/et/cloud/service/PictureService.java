package com.et.cloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.et.cloud.dto.picture.PictureQueryRequest;
import com.et.cloud.dto.picture.PictureReviewRequest;
import com.et.cloud.dto.picture.PictureUploadRequest;
import com.et.cloud.dto.picture.PictureUploadByBatchRequest;
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
     * @param inputSource
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVis uploadPicture(Object inputSource,
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

    /**
     * 图片审核方法
     *
     * @param pictureReviewRequest 查询请求体
     * @param user
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User user);

    /**
     * 这个代码是为了更新审核参数
     *
     * @param picture
     * @param loginUser
     */
    public void fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量抓取图片
     * @param pictureUploadByBatchRequest 上传请求体
     * @param loginUser 上传用户
     * @return
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );

    /**
     * 校验登录用户能不能看到空间图片全新啊
     * @param loginUser
     * @param picture
     */
    void checkPictureAuth(User loginUser, Picture picture);

    /**
     * 删除图片
     *
     * @param pictureId
     * @param loginUser
     */
    void deletePicture(long pictureId, User loginUser);

}

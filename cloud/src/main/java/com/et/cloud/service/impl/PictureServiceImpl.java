package com.et.cloud.service.impl;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.et.cloud.dto.file.UploadPictureResult;
import com.et.cloud.dto.picture.PictureQueryRequest;
import com.et.cloud.dto.picture.PictureUploadRequest;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.manager.FileManager;
import com.et.cloud.model.entity.Picture;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.vis.PictureVis;
import com.et.cloud.model.vis.UserVis;
import com.et.cloud.service.PictureService;
import com.et.cloud.mapper.PictureMapper;
import com.et.cloud.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.jdbc.Null;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * @author origin
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-09-28 16:58:07
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {
//    FileManager fileManager = new FileManager();

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Override
    public PictureVis uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 用于判断是新增还是更新图片
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新图片，需要校验图片是否存在
        if (pictureId != null) {
            boolean exists = this.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .exists();
            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        // 上传图片，得到信息
        // 按照用户 id 划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setName(uploadPictureResult.getPicName());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        //通过检测主键是否有值，判断是上传还是新建
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
        return PictureVis.objToVis(picture);
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        // JSON 数组查询,批量标签匹配
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public PictureVis getPictureVis(Picture picture, HttpServletRequest httpServletRequest) {
        PictureVis pictureVis = PictureVis.objToVis(picture);
        Long userId = picture.getUserId();
        if(   userId != null && userId > 0){
            User user = userService.getById(userId);
            UserVis userVis =userService.getUserVis(user);
            pictureVis.setUser(userVis);
        }
        return pictureVis;
    }

    @Override
    public Page<PictureVis> getPictureVisPage(Page<Picture> picturePage, HttpServletRequest httpServletRequest) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVis> pictureVisPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        //经典判空
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVisPage;
        }
        List<PictureVis> pictureVisList = new ArrayList<>();
        for (Picture picture : pictureList) {
            PictureVis pictureVis = PictureVis.objToVis(picture);
            pictureVisList.add(pictureVis);
        }
        // 2. 收集所有需要查询的用户ID（去重）
        Set<Long> userIdSet = new HashSet<>();
        for (Picture picture : pictureList) {
            Long userId = picture.getUserId();
            if (userId != null) {
                userIdSet.add(userId);
            }
        }
        // 3. 批量查询用户信息，构建 userId -> User 的映射
        Map<Long, User> userIdUserMap = new HashMap<>();
        List<User> users = userService.listByIds(userIdSet);
        for (User user : users) {
            userIdUserMap.put(user.getId(), user);
        }
        // 4. 填充用户信息到 PictureVis
        for (PictureVis pictureVis : pictureVisList) {
            Long userId = pictureVis.getUserId();
            User user = userService.getById(userId);
            UserVis userVis = userService.getUserVis(user);
            pictureVis.setUser(userVis);
        }
        pictureVisPage.setRecords(pictureVisList);
        return pictureVisPage;
    }

    @Override
    public void vaildPicture(Picture picture) {

        Long pictureId = picture.getId();
        ThrowUtils.throwIf(pictureId==null,ErrorCode.NOT_FOUND_ERROR,"id不能为空");

        String url=picture.getUrl();
        if(StrUtil.isNotBlank(url)){
            ThrowUtils.throwIf(url.length()>1024,ErrorCode.PARAMS_ERROR,"URL过长");
        }

//        String introduction=picture.getIntroduction();
//        if(StrUtil.isNotBlank(introduction)){
//            ThrowUtils.throwIf(url.length()>1024,ErrorCode.PARAMS_ERROR,"简介过长");
//        }
//        感觉不是很有需要
    }
}





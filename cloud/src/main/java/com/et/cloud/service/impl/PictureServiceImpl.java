package com.et.cloud.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.et.cloud.api.aliyunai.CreateOutPaintingTaskRequest;
import com.et.cloud.api.aliyunai.CreateOutPaintingTaskResponse;
import com.et.cloud.api.aliyunai.model.AliYunAiApi;
import com.et.cloud.commen.DeleteRequest;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.dto.file.UploadPictureResult;
import com.et.cloud.dto.picture.*;
import com.et.cloud.enums.PictureReviewStatusEnum;
import com.et.cloud.enums.SpaceTypeEnum;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.manager.FileManager;
import com.et.cloud.manager.upload.FilePictureUpload;
import com.et.cloud.manager.upload.PictureUploadTemplate;
import com.et.cloud.manager.upload.UrlPictureUpload;
import com.et.cloud.model.entity.Picture;
import com.et.cloud.model.entity.Space;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.vis.PictureVis;
import com.et.cloud.model.vis.UserVis;
import com.et.cloud.service.PictureService;
import com.et.cloud.mapper.PictureMapper;
import com.et.cloud.service.SpaceService;
import com.et.cloud.service.UserService;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.jdbc.Null;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
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

    @Resource
    private SpaceService spaceService;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Override
    public PictureVis uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
//        空间信息检验
        Space space = null;
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");

            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
//            这里是觉得就算多存一条也无所谓才这样检验，其实百度网盘的逻辑也是这个啊
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }
        // 用于判断是新增还是更新图片
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新图片，需要校验图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
//            只有本人和管理员可以该图片
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
//            没传id就用过去的
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
//如果有传，那么就必须一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }
        // 上传图片，得到信息
        // 按照用户 id 划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
//            为空就是传到公共空间
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
//            传递到自己的空间去
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        //根据inputSource的类型，区分上传方式
        PictureUploadTemplate pictureUploadTemplate=filePictureUpload;
        if(inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
//校验图片大小传参
//        System.out.println("2312312312312312    "+uploadPictureResult.getPicSize());
        ThrowUtils.throwIf(uploadPictureResult.getPicSize() == null, ErrorCode.SYSTEM_ERROR, "图片上传失败，无法获取图片大小");

        // 构造要入库的图片信息
        Picture picture = new Picture();
//        插入空间信息
        picture.setSpaceId(spaceId);
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }

        picture.setName(picName);

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

        fillReviewParams(picture, loginUser,spaceId);
        //通过检测主键是否有值，判断是上传还是新建
//        需要更新空间的额度
        // 在事务外部先获取旧图片信息，以便在事务中使用
//        可以肯定是这里出错了
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
            if (finalSpaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });
//        此外，这里还少一个替换原本图片，占用空间清理空间的事
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
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Boolean nullSpaceId=pictureQueryRequest.isNullSpaceId();
//        时间范围查询，暂定
        Date reviewerTime = pictureQueryRequest.getReviewerTime();



        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
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
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);

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

//    @Override
//    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User user) {
//        //校验参数
//        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.NOT_FOUND_ERROR);
//        Long id = pictureReviewRequest.getId();
//        Integer status = pictureReviewRequest.getReviewStatus();
//        PictureReviewStatusEnum enumstatus = PictureReviewStatusEnum.getEnumByValue(status);
//        String reviewMessage = pictureReviewRequest.getReviewMessage();
//        if (id <= 0 || enumstatus == null || PictureReviewStatusEnum.REVIEWING.equals(enumstatus)) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        //图片是否存在
//        Picture oldpicture = this.getById(id);
//        ThrowUtils.throwIf(oldpicture == null, ErrorCode.NOT_FOUND_ERROR);
//        //是否已经通过审核
//        if (oldpicture.getReviewStatus().equals(PictureReviewStatusEnum.REVIEWING)) {
//            throw new BusinessException(ErrorCode.OPERATION_ERROR, "重复审核");
//        }
//        //数据库操作
//        Picture newpicture = new Picture();
//        BeanUtil.copyProperties(newpicture, pictureReviewRequest);
//        newpicture.setReviewTime(new Date());
//        Boolean result = this.updateById(newpicture);
//        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
//    }以后可能这里检

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1. 参数校验
        Long id = pictureReviewRequest.getId();
        Integer status = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum enumstatus = PictureReviewStatusEnum.getEnumByValue(status);
        if (id <= 0 || enumstatus == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // (可选的额外校验) 管理员不能提交“待审核”决定
        if (PictureReviewStatusEnum.REVIEWING.equals(enumstatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态不能为待审核");
        }

        // 2. 图片是否存在
        Picture oldpicture = this.getById(id);
        ThrowUtils.throwIf(oldpicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 3. 【修正】是否重复审核 (判断新旧状态是否相同)
        if (oldpicture.getReviewStatus().equals(status)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }

        // 4. 【修正】数据库操作
        Picture updatePicture = new Picture();
        updatePicture.setId(id);
        updatePicture.setReviewStatus(status);
        updatePicture.setReviewMessage(pictureReviewRequest.getReviewMessage());
        updatePicture.setReviewerId(loginUser.getId()); // 设置审核人
        updatePicture.setReviewTime(new Date());      // 设置审核时间

        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }


    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    @Override
    @Deprecated
    public void fillReviewParams(Picture picture, User loginUser) {
        fillReviewParams(picture, loginUser, picture.getSpaceId());
//        if (userService.isAdmin(loginUser)) {
//
//            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
//            picture.setReviewMessage("管理员自动过审");
//            picture.setReviewTime(new Date());
//        } else {
//            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
//        }
    }
//    看来是有什么东西一直在调用这个函数

    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     * @param spaceId 所属空间，可能为 null（表示公共空间）
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser, Long spaceId) {
        // 🔍 添加调试日志
        log.info("========== fillReviewParams 调试信息 ==========");
        log.info("传入的 spaceId: {}", spaceId);
        log.info("是否为管理员: {}", userService.isAdmin(loginUser));

        if (spaceId == null) {
            log.info("执行分支: 公共空间");
            // 公共空间
            if (userService.isAdmin(loginUser)) {
                picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
                picture.setReviewMessage("管理员自动过审");
                picture.setReviewTime(new Date());
                picture.setReviewerId(loginUser.getId());
            } else {
                picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
                picture.setReviewMessage("公共空间，待审核");
            }
        } else {
            log.info("执行分支: 私有/团队空间");
            // 私有空间/团队空间，自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewMessage("私有空间自动过审_spaceId=" + spaceId);
            picture.setReviewTime(new Date());
            picture.setReviewerId(loginUser.getId());
        }

        log.info("最终设置的审核状态: {}", picture.getReviewStatus());
        log.info("最终设置的审核消息: {}", picture.getReviewMessage());
        log.info("==========================================");
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 名称前缀默认等于搜索关键词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        // 遍历元素，依次处理上传图片
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片的地址，防止转义或者和对象存储冲突的问题
            // codefather.cn?yupi=dog，应该只保留 codefather.cn
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                PictureVis pictureVis = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功，id = {}", pictureVis.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
//        权限矫正
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限，已经改为使用注解鉴权
//        checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 更新空间的使用额度，释放额度
            boolean update = spaceService.lambdaUpdate()
                    .eq(Space::getId, oldPicture.getSpaceId())
                    .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                    .setSql("totalCount = totalCount - 1")
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            return true;
        });
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {//        想一想要使用AI扩图要做什么
        //肯定都是有报错检测，什么输入是否为空之类的
//        但是先不说那些，第一步我觉得是要接受前端的请求，发送到API去。查看是否连接成功。我们应该不是直接负责AI扩图，这个函数只需要做到发送，接收，包装起来就行
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR,"图片不存在");
//        权限校验，这个有点没想到，应该认为这是一个定式思维。报错检验-权限检验
        Picture picture = this.getById(pictureId);
//        checkPictureAuth(loginUser,this.getById(pictureId));
//      然后就是创建扩图任务，填写里面的参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
//        不要信任DTO的url，要自己设置
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);
//        最后得到的结果就是一个任务ID
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);

        return aliYunAiApi.createOutPaintingTask(taskRequest);
    }


}





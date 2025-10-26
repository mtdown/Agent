package com.et.cloud.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.et.cloud.dto.file.UploadPictureResult;
import com.et.cloud.dto.picture.PictureQueryRequest;
import com.et.cloud.dto.picture.PictureReviewRequest;
import com.et.cloud.dto.picture.PictureUploadByBatchRequest;
import com.et.cloud.dto.picture.PictureUploadRequest;
import com.et.cloud.dto.space.SpaceAddRequest;
import com.et.cloud.dto.space.SpaceQueryRequest;
import com.et.cloud.enums.PictureReviewStatusEnum;
import com.et.cloud.enums.SpaceLevelEnum;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.manager.FileManager;
import com.et.cloud.manager.upload.FilePictureUpload;
import com.et.cloud.manager.upload.PictureUploadTemplate;
import com.et.cloud.manager.upload.UrlPictureUpload;
import com.et.cloud.mapper.PictureMapper;
import com.et.cloud.mapper.SpaceMapper;
import com.et.cloud.mapper.UserMapper;
import com.et.cloud.model.entity.Picture;
import com.et.cloud.model.entity.Space;

import com.et.cloud.model.entity.User;
import com.et.cloud.model.vis.PictureVis;
import com.et.cloud.model.vis.SpaceVis;
import com.et.cloud.model.vis.UserVis;
import com.et.cloud.service.SpaceService;
import com.et.cloud.service.UserService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService {
    @Resource
    private UserService userService;

    @Override
    public void vaildSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);

        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);

        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }

        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public SpaceVis getspaceVis(Space space, HttpServletRequest httpServletRequest) {
        SpaceVis spaceVis = SpaceVis.objToVis(space);
        Long userId = space.getUserId();
        if(   userId != null && userId > 0){
            User user = userService.getById(userId);
            UserVis userVis =userService.getUserVis(user);
            spaceVis.setUser(userVis);
        }
        return spaceVis;
    }

    @Override
    public Page<SpaceVis> getSpaceVisPage(Page<Space> spacePage, HttpServletRequest httpServletRequest) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVis> spaceVisPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        //经典判空
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVisPage;
        }
        List<SpaceVis> spaceVisList = new ArrayList<>();
        for (Space space : spaceList) {
            SpaceVis spaceVis = SpaceVis.objToVis(space);
            spaceVisList.add(spaceVis);
        }
        // 2. 收集所有需要查询的用户ID（去重）
        Set<Long> userIdSet = new HashSet<>();
        for (Space space : spaceList) {
            Long userId = space.getUserId();
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
        // 4. 填充用户信息到 SpaceVis
        for (SpaceVis spaceVis : spaceVisList) {
            Long userId = spaceVis.getUserId();
            User user = userService.getById(userId);
            UserVis userVis = userService.getUserVis(user);
            spaceVis.setUser(userVis);
        }
        spaceVisPage.setRecords(spaceVisList);
        return spaceVisPage;
    }

    @Override
    public void fillSpaceBySpaceLevel(Space space) {

        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {

        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);

        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }

        this.fillSpaceBySpaceLevel(space);

        this.vaildSpace(space, true);
        Long userId = loginUser.getId();
        space.setUserId(userId);

        if (SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        //这里允许不同用户同时执行，那么就可以把锁放在单个用户实行空间创建的地方
        //.intern() 的作用是确保对于同一个 userId，所有线程获取到的锁对象 (lock) 都是内存中的同一个实例。
//        如果不是同一个实例，那么在单个用户进行多次表创建就会导致加的锁失效
        String lock = String.valueOf(userId).intern();
//        这里是使用synchronized实现的
        synchronized (lock) {
//            这里使用编程性事务，声明型的事务可能导致重复读取，所以这里就要用编程性事务
            Long newSpaceId = transactionTemplate.execute(status -> {
//                QueryWrapper 是非类型安全的。如果不小心拼错了列名，只有在程序运行时才会遇到问题
//                LambdaQueryWrapper 是类型安全.eq(Space::getUserId, userId)就是直接关联到你的 Space 实体类
//                exists是找记录，count是数条数，所以当我们判断存在不存在的适合用exist更好
                boolean exists = this.lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .exists();
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户仅能有一个私有空间");

                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

                return space.getId();
            });
//声明式事务和编程性事务private TransactionTemplate transactionTemplate;这个就算编程性事务
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }

    }
}





package com.et.cloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.et.cloud.dto.space.SpaceAddRequest;
import com.et.cloud.dto.space.SpaceQueryRequest;
import com.et.cloud.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.vis.SpaceVis;

import javax.servlet.http.HttpServletRequest;

/**
 * @author origin
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2025-10-11 10:12:32
 */
public interface SpaceService extends IService<Space> {

    /**
     * 校验
     * @param space
     * @param add
     */
    void vaildSpace(Space space, boolean add);

    /**
     * 获取查询对象
     * @param spaceQueryRequest
     * @return
     */

    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 获取空间包装类
     * @param space
     * @param httpServletRequest
     * @return
     */
    SpaceVis getspaceVis(Space space, HttpServletRequest httpServletRequest);

    /**
     * 获取空间包装类分页
     * @param spacePage
     * @param httpServletRequest
     * @return
     */
    Page<SpaceVis> getSpaceVisPage(Page<Space> spacePage, HttpServletRequest httpServletRequest);

    /**
     * 根据空间级别自动填充限额数据
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);

    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);


}

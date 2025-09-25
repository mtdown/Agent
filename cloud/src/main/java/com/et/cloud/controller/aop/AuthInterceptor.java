package com.et.cloud.controller.aop;


import com.et.cloud.annotation.AuthCheck;
import com.et.cloud.enums.UserRoleEnum;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.model.entity.User;
import com.et.cloud.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 执行拦截
     *
     * @param joinPoint 切入点
     * @param authCheck 权限校验注解
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        //拿到我要校验的权限
        String mustRole = authCheck.mustRole();
        // 这几行是 Spring 的标准操作，用来拿到当前的 HttpServletRequest 对象。
        // 因为用户信息（Session/Token）通常都存放在 request 里。
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 当前登录用户
        // 调用 UserService，从 request 中解析出当前登录的用户对象。
        User loginUser = userService.getLoginUser(request);
        // 将字符串的角色转换为枚举类型，让代码更健壮、更安全。然后就是进入判断流程
        UserRoleEnum mustRoleEnum = UserRoleEnum.getUserRoleEnum(mustRole);

        // 不需要权限，放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 以下为：必须有该权限才通过
        // 获取当前用户具有的权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getUserRoleEnum(loginUser.getUserRole());
        // 没有权限，拒绝
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 要求必须有管理员权限，但用户没有管理员权限，拒绝
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 通过权限校验，放行
        return joinPoint.proceed();
    }
}


package com.et.cloud.config;

import cn.dev33.satoken.SaManager;
import com.et.cloud.manager.auth.StpKit;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * sa-token 多账号体系的启动注册。
 *
 * StpLogic("space") 的注册发生在 StpKit 类加载时，而注解中的
 * type = StpKit.SPACE_TYPE 是编译期内联的字符串常量，不会触发类加载。
 * 冷启动后若首个携带 @SaSpaceCheckPermission 的请求先于任何 StpKit
 * 代码执行（例如用户带着 Redis 存活会话直接访问图片详情），
 * SaManager.getStpLogic("space") 会抛"未能获取对应 StpLogic"。
 * 启动时显式注册，保证与请求顺序无关。
 */
@Configuration
public class StpKitRegisterConfig {

    @PostConstruct
    public void registerSpaceStpLogic() {
        SaManager.putStpLogic(StpKit.SPACE);
    }
}

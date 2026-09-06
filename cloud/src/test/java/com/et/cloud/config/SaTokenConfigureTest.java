package com.et.cloud.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import cn.dev33.satoken.stp.StpLogic;
import com.et.cloud.config.StpKitRegisterConfig;
import com.et.cloud.manager.auth.StpKit;
import com.et.cloud.manager.auth.annoation.SaSpaceCheckPermission;
import com.et.cloud.manager.auth.annoation.SaTokenConfigure;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SaTokenConfigureTest {

    @SaSpaceCheckPermission("picture:view")
    private void protectedPicture() {
    }

    @Test
    void startupRegistersSpaceLogicBeforeLoginAndSupportsContextRestart() throws Exception {
        Map<String, StpLogic> previousLogics = new HashMap<>(SaManager.stpLogicMap);
        var previousAnnotationStrategy = SaAnnotationStrategy.instance.getAnnotation;
        try {
            // No login or StpKit.SPACE access before bootstrapping the configuration.
            for (int startup = 0; startup < 2; startup++) {
                SaManager.removeStpLogic("space");
                try (var context = new AnnotationConfigApplicationContext(
                        SaTokenConfigure.class, StpKitRegisterConfig.class)) {
                    StpLogic registered = assertDoesNotThrow(() -> SaManager.getStpLogic("space"));
                    assertSame(StpKit.SPACE, registered);
                    SaCheckPermission permission = AnnotatedElementUtils.getMergedAnnotation(
                            getClass().getDeclaredMethod("protectedPicture"), SaCheckPermission.class);
                    assertNotNull(permission);
                    assertSame(registered, SaManager.getStpLogic(permission.type()));
                    assertArrayEquals(new String[]{"picture:view"}, permission.value());
                }
            }
        } finally {
            SaManager.stpLogicMap.clear();
            SaManager.stpLogicMap.putAll(previousLogics);
            SaAnnotationStrategy.instance.getAnnotation = previousAnnotationStrategy;
        }
    }
}

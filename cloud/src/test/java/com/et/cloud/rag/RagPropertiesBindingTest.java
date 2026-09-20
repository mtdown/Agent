package com.et.cloud.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住 {@code rag.llm.enable-thinking} 的绑定契约。
 *
 * <p>背景：application.yml 里该键写作 {@code ${RAG_LLM_ENABLE_THINKING:}}，未设环境变量时值为空串。
 * 曾尝试用 {@code ${RAG_LLM_ENABLE_THINKING:#{null}}} 表达"缺省即 null"，但 SpEL 默认值在
 * {@code @ConfigurationProperties} 绑定时不会被求值 —— 字面量 {@code #{null}} 会参与 Boolean 转换，
 * 直接让应用启动失败（由 CloudApplicationTests.contextLoads 捕获）。
 *
 * <p>本测试用内存 PropertySource 直接锁死三种情形，避免再退回到那个写法。
 */
class RagPropertiesBindingTest {

    private static RagProperties bind(Map<String, Object> source) {
        Binder binder = new Binder(new MapConfigurationPropertySource(source));
        return binder.bind("rag", Bindable.of(RagProperties.class))
                .orElseGet(RagProperties::new);
    }

    @Test
    void blankValueBindsToNullSoFieldIsNotSent() {
        Map<String, Object> source = new HashMap<>();
        source.put("rag.llm.enable-thinking", "");

        RagProperties.Llm llm = bind(source).getLlm();

        assertNull(llm.getEnableThinking(), "空串必须绑定为 null，否则无法区分「未配置」");
        assertFalse(llm.hasEnableThinking(), "未配置时不得下发 enable_thinking");
    }

    @Test
    void explicitValuesBindToBoolean() {
        Map<String, Object> off = new HashMap<>();
        off.put("rag.llm.enable-thinking", "false");
        RagProperties.Llm llmOff = bind(off).getLlm();
        assertEquals(Boolean.FALSE, llmOff.getEnableThinking());
        assertTrue(llmOff.hasEnableThinking());

        Map<String, Object> on = new HashMap<>();
        on.put("rag.llm.enable-thinking", "true");
        assertEquals(Boolean.TRUE, bind(on).getLlm().getEnableThinking());
    }

    @Test
    void absentKeyLeavesFieldNull() {
        RagProperties.Llm llm = bind(new HashMap<>()).getLlm();

        assertNull(llm.getEnableThinking());
        assertFalse(llm.hasEnableThinking());
    }
}

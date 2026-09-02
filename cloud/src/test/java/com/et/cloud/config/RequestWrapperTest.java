package com.et.cloud.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.ServletInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestWrapperTest {

    @Test
    void shouldPreserveUtf8RequestBody() throws Exception {
        String body = "{\"title\":\"测试文档\",\"content\":\"这里是中文正文\"}";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent(body.getBytes(StandardCharsets.UTF_8));

        RequestWrapper requestWrapper = new RequestWrapper(request);

        assertEquals(body, requestWrapper.getBody());
        assertEquals(body, readBody(requestWrapper.getInputStream()));
        assertEquals(body, readBody(requestWrapper.getInputStream()));
    }

    private String readBody(ServletInputStream inputStream) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

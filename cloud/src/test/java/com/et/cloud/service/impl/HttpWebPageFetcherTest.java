package com.et.cloud.service.impl;

import com.et.cloud.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * URL safety, scheme handling and decoding of the HTTP fetcher.
 */
class HttpWebPageFetcherTest {

    private final HttpWebPageFetcher fetcher = new HttpWebPageFetcher();

    @Test
    void rejectsLoopbackAddress() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fetcher.fetch("http://127.0.0.1:8123/api/v2/api-docs"));
        assertTrue(exception.getMessage().contains("内网或本地地址"), exception.getMessage());
    }

    @Test
    void rejectsLocalhostHostname() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fetcher.fetch("http://localhost/admin"));
        assertTrue(exception.getMessage().contains("内网或本地地址"), exception.getMessage());
    }

    @Test
    void rejectsLinkLocalAndPrivateAndMulticastAddresses() {
        assertTrue(assertThrows(BusinessException.class,
                () -> fetcher.fetch("http://169.254.169.254/latest/meta-data/"))
                .getMessage().contains("内网或本地地址"));
        assertTrue(assertThrows(BusinessException.class, () -> fetcher.fetch("http://192.168.1.10/"))
                .getMessage().contains("内网或本地地址"));
        assertTrue(assertThrows(BusinessException.class, () -> fetcher.fetch("http://10.0.0.5/"))
                .getMessage().contains("内网或本地地址"));
        assertTrue(assertThrows(BusinessException.class, () -> fetcher.fetch("http://224.0.0.1/"))
                .getMessage().contains("内网或本地地址"));
    }

    @Test
    void rejectsNonHttpSchemesBeforeAnyConnection() {
        assertEquals("仅支持 http 或 https 地址",
                assertThrows(BusinessException.class, () -> fetcher.fetch("ftp://example.com/a.html"))
                        .getMessage());
        assertEquals("仅支持 http 或 https 地址",
                assertThrows(BusinessException.class, () -> fetcher.fetch("file:///C:/secret.txt"))
                        .getMessage());
    }

    @Test
    void rejectsMalformedUrl() {
        assertEquals("URL 格式不正确",
                assertThrows(BusinessException.class, () -> fetcher.fetch("not a url")).getMessage());
    }

    @Test
    void readsCharsetFromContentTypeHeader() {
        assertEquals("utf-8", ReflectionTestUtils.invokeMethod(fetcher, "charsetFromContentType",
                "text/html; charset=utf-8"));
        assertEquals("gb2312", ReflectionTestUtils.invokeMethod(fetcher, "charsetFromContentType",
                "text/html;charset=GB2312"));
        assertNull(ReflectionTestUtils.invokeMethod(fetcher, "charsetFromContentType", "text/html"));
    }

    @Test
    void decodesGb18030BodyWhenNoCharsetIsDeclared() {
        String text = "重庆市人民政府关于印发政务服务管理办法的通知正文内容";
        byte[] gb18030 = text.getBytes(Charset.forName("GB18030"));

        String decoded = ReflectionTestUtils.invokeMethod(fetcher, "decode", gb18030, null);

        assertEquals(text, decoded);
    }

    @Test
    void decodesUtf8BodyWhenNoCharsetIsDeclared() {
        String text = "统一的 UTF-8 网页正文内容";
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);

        assertEquals(text, ReflectionTestUtils.invokeMethod(fetcher, "decode", utf8, null));
    }

    @Test
    void failsWhenResponseExceedsSizeLimit() throws IOException {
        ReflectionTestUtils.setField(fetcher, "maxResponseBytes", 4L);
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[64]));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(fetcher, "readLimited", connection));

        assertTrue(exception.getMessage().contains("响应内容超过"), exception.getMessage());
    }
}

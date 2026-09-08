package com.et.cloud.service.impl;

import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.model.dto.FetchedPage;
import com.et.cloud.service.WebPageFetcher;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads webpages over plain {@link HttpURLConnection}.
 *
 * Redirects are followed manually so every hop is validated: a redirect pointing from a public host
 * to {@code 127.0.0.1} or {@code 169.254.169.254} must be rejected exactly like a directly submitted
 * private address.
 */
@Service
@Slf4j
public class HttpWebPageFetcher implements WebPageFetcher {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    private static final String ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9";

    private static final Pattern META_CHARSET = Pattern.compile(
            "<meta[^>]+charset=[\"']?\\s*([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE);

    private static final int CHARSET_SNIFF_LENGTH = 4096;

    @Value("${wiki.batch-import.connect-timeout-ms:10000}")
    private int connectTimeoutMs = 10000;

    @Value("${wiki.batch-import.read-timeout-ms:20000}")
    private int readTimeoutMs = 20000;

    @Value("${wiki.batch-import.max-redirects:3}")
    private int maxRedirects = 3;

    @Value("${wiki.batch-import.max-response-bytes:5242880}")
    private long maxResponseBytes = 5 * 1024 * 1024L;

    @Override
    public FetchedPage fetch(String url) throws IOException {
        String current = url;
        for (int hop = 0; hop <= maxRedirects; hop++) {
            assertSafeUrl(current);
            HttpURLConnection connection = open(current);
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        throw new BusinessException(ErrorCode.OPERATION_ERROR, "下载失败，重定向缺少目标地址");
                    }
                    current = new URL(new URL(current), location.trim()).toExternalForm();
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "下载失败，HTTP " + status);
                }
                byte[] body = readLimited(connection);
                String charsetName = resolveCharset(connection, body);
                String html = decode(body, charsetName);
                FetchedPage page = new FetchedPage();
                page.setHtml(html);
                page.setFinalUrl(current);
                page.setPageTitle(extractTitle(html));
                return page;
            } finally {
                connection.disconnect();
            }
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "下载失败，重定向次数超过 " + maxRedirects + " 次");
    }

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);
        return connection;
    }

    /**
     * Rejects anything that is not a public HTTP(S) target. Called for the submitted url and for
     * every redirect hop.
     */
    private void assertSafeUrl(String url) {
        URI uri;
        try {
            uri = new URI(url == null ? "" : url.trim());
        } catch (URISyntaxException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "URL 格式不正确");
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 http 或 https 地址");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "URL 缺少主机名");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法解析域名：" + host);
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || address.isAnyLocalAddress()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "URL 指向内网或本地地址，已拒绝访问");
            }
        }
    }

    private byte[] readLimited(HttpURLConnection connection) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream input = connection.getInputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                if (buffer.size() + read > maxResponseBytes) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "响应内容超过 " + (maxResponseBytes / 1024 / 1024) + "MB 限制");
                }
                buffer.write(chunk, 0, read);
            }
        }
        return buffer.toByteArray();
    }

    private String resolveCharset(HttpURLConnection connection, byte[] body) {
        String contentType = connection.getHeaderField("Content-Type");
        String fromHeader = charsetFromContentType(contentType);
        if (fromHeader != null) {
            return fromHeader;
        }
        String head = new String(body, 0, Math.min(body.length, CHARSET_SNIFF_LENGTH), StandardCharsets.UTF_8);
        Matcher matcher = META_CHARSET.matcher(head);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String charsetFromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        for (String part : contentType.split(";")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (trimmed.startsWith("charset=")) {
                String value = trimmed.substring("charset=".length()).trim().replace("\"", "");
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /**
     * Mirrors the reference downloader: declared charset first, then UTF-8, then GB18030, because
     * Chinese government and news sites are still frequently served as GB18030.
     */
    private String decode(byte[] body, String charsetName) {
        if (charsetName != null && !charsetName.isBlank()) {
            try {
                return new String(body, Charset.forName(charsetName.trim()));
            } catch (Exception e) {
                log.debug("unsupported charset {}, falling back to utf-8/gb18030", charsetName);
            }
        }
        String utf8 = tryDecode(body, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return utf8;
        }
        String gb18030 = tryDecode(body, Charset.forName("GB18030"));
        return gb18030 != null ? gb18030 : new String(body, StandardCharsets.UTF_8);
    }

    private String tryDecode(byte[] body, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(body));
            return decoded.toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private String extractTitle(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        try {
            Document document = Jsoup.parse(html);
            return document.title() == null ? "" : document.title().trim();
        } catch (Exception e) {
            log.debug("failed to read page title", e);
            return "";
        }
    }
}

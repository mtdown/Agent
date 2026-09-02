package com.et.cloud.config;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 包装请求，使 InputStream 可以重复读取
 *
 * @author pine
 */
@Slf4j
public class RequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    public RequestWrapper(HttpServletRequest request) {
        super(request);
        byte[] requestBody = new byte[0];
        try (InputStream inputStream = request.getInputStream(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            requestBody = outputStream.toByteArray();
        } catch (IOException ignored) {
        }
        body = requestBody;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }
        };

    }

    @Override
    public BufferedReader getReader() throws IOException {
        String characterEncoding = getCharacterEncoding();
        Charset charset = characterEncoding == null ? StandardCharsets.UTF_8 : Charset.forName(characterEncoding);
        return new BufferedReader(new InputStreamReader(this.getInputStream(), charset));
    }

    public String getBody() {
        String characterEncoding = getCharacterEncoding();
        Charset charset = characterEncoding == null ? StandardCharsets.UTF_8 : Charset.forName(characterEncoding);
        return new String(this.body, charset);
    }

}

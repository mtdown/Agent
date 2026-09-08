package com.et.cloud.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.model.dto.ImportedWikiDocument;
import com.et.cloud.service.WikiDocumentImportService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WikiDocumentImportServiceImpl implements WikiDocumentImportService {

    private static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024L;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("md", "html", "htm");

    @Override
    public ImportedWikiDocument parse(MultipartFile multipartFile, String title) {
        ThrowUtils.throwIf(multipartFile == null || multipartFile.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        ThrowUtils.throwIf(multipartFile.getSize() > MAX_DOCUMENT_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过 10M");
        String originalFilename = multipartFile.getOriginalFilename();
        String extension = extensionOf(originalFilename);
        ThrowUtils.throwIf(StrUtil.isBlank(extension) || !ALLOWED_EXTENSIONS.contains(extension),
                ErrorCode.PARAMS_ERROR, "仅支持 md、html、htm 文件");

        String contentFormat;
        String content;
        if ("md".equals(extension)) {
            contentFormat = "markdown";
            content = readText(multipartFile);
        } else {
            // Uploaded HTML pages are cleaned into structured Markdown instead of being stored
            // raw: downloaded pages reference sibling CSS/JS/image resources that a single-file
            // import cannot bring along, so faithful rendering is not achievable. The cleaner
            // keeps h1-h6 headings and plain text blocks; images and page chrome are dropped.
            contentFormat = "markdown";
            content = HtmlToMarkdownConverter.convert(readText(multipartFile));
            ThrowUtils.throwIf(StrUtil.isBlank(content), ErrorCode.PARAMS_ERROR, "未能从 HTML 中提取有效文本内容");
        }
        ThrowUtils.throwIf(StrUtil.isBlank(content), ErrorCode.PARAMS_ERROR, "文件内容不能为空");

        ImportedWikiDocument imported = new ImportedWikiDocument();
        imported.setTitle(resolveTitle(title, originalFilename));
        imported.setContent(content);
        imported.setContentFormat(contentFormat);
        imported.setSourceType("UPLOAD");
        imported.setMetadataJson(buildMetadataJson(multipartFile, extension, contentFormat));
        return imported;
    }

    private String readText(MultipartFile multipartFile) {
        try {
            return new String(multipartFile.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取文件失败");
        }
    }

    private String buildMetadataJson(MultipartFile multipartFile, String extension, String contentFormat) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceFileName", multipartFile.getOriginalFilename());
        metadata.put("sourceExtension", extension);
        metadata.put("sourceMimeType", multipartFile.getContentType());
        metadata.put("sourceSize", multipartFile.getSize());
        metadata.put("importedAs", contentFormat);
        metadata.put("importedAt", Instant.now().toString());
        return JSONUtil.toJsonStr(metadata);
    }

    private String resolveTitle(String title, String originalFilename) {
        if (StrUtil.isNotBlank(title)) {
            return title.trim();
        }
        String filename = StrUtil.blankToDefault(originalFilename, "未命名文档");
        int dotIndex = filename.lastIndexOf('.');
        String baseName = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        return StrUtil.blankToDefault(baseName.trim(), "未命名文档");
    }

    private String extensionOf(String filename) {
        if (StrUtil.isBlank(filename)) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}

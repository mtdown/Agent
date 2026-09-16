package com.et.cloud.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.et.cloud.dto.documentWiki.DocumentWikiBatchUrlImportRequest;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.model.dto.BatchImportItemResult;
import com.et.cloud.model.dto.FetchedPage;
import com.et.cloud.model.dto.ImportedWikiDocument;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiFolder;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.WikiBatchImportService;
import com.et.cloud.service.WikiCacheManager;
import com.et.cloud.service.WikiDocumentImportService;
import com.et.cloud.service.WikiFolderService;
import com.et.cloud.service.WikiSpaceService;
import com.et.cloud.service.WebPageFetcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class WikiBatchImportServiceImpl implements WikiBatchImportService {

    private static final int MAX_TITLE_LENGTH = 128;

    /**
     * A JSON corpus is submitted whole, so the entry count is unbounded and the file size is the
     * only guard. Kept equal to the servlet {@code spring.servlet.multipart.max-file-size}.
     */
    private static final long MAX_JSON_FILE_SIZE = 30L * 1024 * 1024;

    @Value("${wiki.batch-import.max-items:20}")
    private int maxItems = 20;

    @Resource
    private WikiSpaceService wikiSpaceService;

    @Resource
    private WikiFolderService wikiFolderService;

    @Resource
    private DocumentWikiService documentWikiService;

    @Resource
    private WikiDocumentImportService wikiDocumentImportService;

    @Resource
    private WikiCacheManager wikiCacheManager;

    @Resource
    private WebPageFetcher webPageFetcher;

    @Override
    public List<BatchImportItemResult> importUrls(DocumentWikiBatchUrlImportRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        List<String> urls = splitUrls(request.getUrls());
        ThrowUtils.throwIf(urls.isEmpty(), ErrorCode.PARAMS_ERROR, "请至少填写一个网页地址");
        ThrowUtils.throwIf(urls.size() > maxItems, ErrorCode.PARAMS_ERROR,
                "一次最多导入 " + maxItems + " 个网页地址");
        WikiSpace wikiSpace = wikiSpaceService.requireEditableSpace(request.getSpaceId(), loginUser);
        Long folderId = resolveFolderId(request.getFolderId(), wikiSpace, loginUser);

        List<BatchImportItemResult> results = new ArrayList<>(urls.size());
        for (String url : urls) {
            try {
                FetchedPage page = webPageFetcher.fetch(url);
                String markdown = WebPageMarkdownCleaner.toMarkdown(page.getHtml());
                if (StrUtil.isBlank(markdown)) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "未能从页面提取有效文本内容");
                }
                ImportedWikiDocument imported = new ImportedWikiDocument();
                imported.setTitle(resolveUrlTitle(page));
                imported.setContent(markdown);
                imported.setContentFormat("markdown");
                imported.setSourceType("URL");
                imported.setSourceUrl(StrUtil.blankToDefault(page.getFinalUrl(), url));
                imported.setMetadataJson(buildUrlMetadata(page, url));
                Long documentId = saveImported(imported, wikiSpace, folderId, loginUser);
                results.add(BatchImportItemResult.success(url, documentId, imported.getTitle()));
            } catch (Exception e) {
                log.warn("batch url import failed for {}", url, e);
                results.add(BatchImportItemResult.failed(url, failureMessage(e)));
            }
        }
        return results;
    }

    @Override
    public List<BatchImportItemResult> importFiles(Long spaceId, Long folderId, List<MultipartFile> files,
                                                   User loginUser) {
        List<MultipartFile> uploadFiles = files == null ? Collections.emptyList() : files;
        ThrowUtils.throwIf(uploadFiles.isEmpty(), ErrorCode.PARAMS_ERROR, "请至少选择一个文件");
        ThrowUtils.throwIf(uploadFiles.size() > maxItems, ErrorCode.PARAMS_ERROR,
                "一次最多导入 " + maxItems + " 个文件");
        WikiSpace wikiSpace = wikiSpaceService.requireEditableSpace(spaceId, loginUser);
        Long targetFolderId = resolveFolderId(folderId, wikiSpace, loginUser);

        List<BatchImportItemResult> results = new ArrayList<>(uploadFiles.size());
        for (MultipartFile file : uploadFiles) {
            String input = file.getOriginalFilename() == null ? "未命名文件" : file.getOriginalFilename();
            try {
                ImportedWikiDocument imported = wikiDocumentImportService.parse(file, null);
                Long documentId = saveImported(imported, wikiSpace, targetFolderId, loginUser);
                results.add(BatchImportItemResult.success(input, documentId, imported.getTitle()));
            } catch (Exception e) {
                log.warn("batch file import failed for {}", input, e);
                results.add(BatchImportItemResult.failed(input, failureMessage(e)));
            }
        }
        return results;
    }

    @Override
    public List<BatchImportItemResult> importJson(Long spaceId, Long folderId, MultipartFile file, User loginUser) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "请选择要导入的 JSON 文件");
        ThrowUtils.throwIf(file.getSize() > MAX_JSON_FILE_SIZE, ErrorCode.PARAMS_ERROR,
                "JSON 文件大小不能超过 " + (MAX_JSON_FILE_SIZE / 1024 / 1024) + "M");
        WikiSpace wikiSpace = wikiSpaceService.requireEditableSpace(spaceId, loginUser);
        Long targetFolderId = resolveFolderId(folderId, wikiSpace, loginUser);

        // entries are read, imported and released one at a time; a bad entry only costs its own result
        List<BatchImportItemResult> results = new ArrayList<>();
        int entries = JsonDocumentSplitter.split(readBytes(file), imported -> {
            String input = entryInput(imported);
            try {
                Long documentId = saveImported(imported, wikiSpace, targetFolderId, loginUser);
                results.add(BatchImportItemResult.success(input, documentId, imported.getTitle()));
            } catch (Exception e) {
                log.warn("batch json import failed for {}", input, e);
                results.add(BatchImportItemResult.failed(input, failureMessage(e)));
            }
        });
        ThrowUtils.throwIf(entries == 0, ErrorCode.PARAMS_ERROR, "JSON 文件中没有可导入的条目");
        return results;
    }

    private Long saveImported(ImportedWikiDocument imported, WikiSpace wikiSpace, Long folderId, User loginUser) {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setTitle(imported.getTitle());
        documentWiki.setContent(imported.getContent());
        documentWiki.setContentFormat(imported.getContentFormat());
        documentWiki.setSourceType(imported.getSourceType());
        documentWiki.setSourceUrl(imported.getSourceUrl());
        documentWiki.setMetadataJson(imported.getMetadataJson());
        documentWiki.setTags(JSONUtil.toJsonStr(Collections.emptyList()));
        documentWiki.setSummary(documentWikiService.buildSummary(imported.getContent()));
        documentWiki.setUserId(loginUser.getId());
        documentWiki.setSpaceId(wikiSpace.getId());
        documentWiki.setFolderId(folderId);
        documentWiki.setViewCount(0L);
        documentWiki.setEditTime(new Date());
        documentWikiService.validDocumentWiki(documentWiki);
        boolean saved = documentWikiService.save(documentWiki);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "保存文档失败");
        wikiCacheManager.clearSpace(wikiSpace.getId());
        return documentWiki.getId();
    }

    /**
     * Stable per-entry identifier for the result list: the entry's url when it has one, otherwise
     * its title. Never the array position, so a skipped or reordered entry cannot silently shift
     * the source-entry -> document mapping the evaluation harness relies on.
     */
    private String entryInput(ImportedWikiDocument imported) {
        return StrUtil.isNotBlank(imported.getSourceUrl()) ? imported.getSourceUrl() : imported.getTitle();
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取文件失败");
        }
    }

    private Long resolveFolderId(Long folderId, WikiSpace wikiSpace, User loginUser) {
        if (folderId == null) {
            return null;
        }
        WikiFolder folder = wikiFolderService.requireVisibleFolder(folderId, wikiSpace.getId(), loginUser);
        return folder.getId();
    }

    private List<String> splitUrls(List<String> urls) {
        List<String> normalized = new ArrayList<>();
        if (urls == null) {
            return normalized;
        }
        for (String entry : urls) {
            if (StrUtil.isBlank(entry)) {
                continue;
            }
            for (String line : entry.split("[\\r\\n,]+")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        return normalized;
    }

    private String resolveUrlTitle(FetchedPage page) {
        String title = StrUtil.blankToDefault(page.getPageTitle(), "").trim();
        if (!title.isEmpty()) {
            return truncate(title);
        }
        String fallback = page.getFinalUrl();
        try {
            URI uri = new URI(fallback);
            String path = uri.getPath() == null ? "" : uri.getPath();
            int slashIndex = path.lastIndexOf('/');
            String lastSegment = slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
            lastSegment = lastSegment.replaceAll("\\.html?$", "");
            if (!lastSegment.isBlank()) {
                return truncate(lastSegment);
            }
            return truncate(uri.getHost() == null ? fallback : uri.getHost());
        } catch (Exception e) {
            return truncate(fallback);
        }
    }

    private String truncate(String value) {
        return value.length() <= MAX_TITLE_LENGTH ? value : value.substring(0, MAX_TITLE_LENGTH);
    }

    private String buildUrlMetadata(FetchedPage page, String requestedUrl) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceUrl", StrUtil.blankToDefault(page.getFinalUrl(), requestedUrl));
        metadata.put("requestedUrl", requestedUrl);
        metadata.put("sourceType", "URL");
        metadata.put("importedAs", "markdown");
        metadata.put("importedAt", Instant.now().toString());
        return JSONUtil.toJsonStr(metadata);
    }

    private String failureMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}

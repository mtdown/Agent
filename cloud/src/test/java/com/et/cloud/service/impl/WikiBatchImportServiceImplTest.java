package com.et.cloud.service.impl;

import com.et.cloud.dto.documentWiki.DocumentWikiBatchUrlImportRequest;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.model.dto.BatchImportItemResult;
import com.et.cloud.model.dto.FetchedPage;
import com.et.cloud.model.dto.ImportedWikiDocument;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiFolder;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.WikiCacheManager;
import com.et.cloud.service.WikiDocumentImportService;
import com.et.cloud.service.WikiFolderService;
import com.et.cloud.service.WikiSpaceService;
import com.et.cloud.service.WebPageFetcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Batch import orchestration: per-item isolation, destination checks and stored metadata.
 */
class WikiBatchImportServiceImplTest {

    private final WikiSpaceService wikiSpaceService = mock(WikiSpaceService.class);
    private final WikiFolderService wikiFolderService = mock(WikiFolderService.class);
    private final DocumentWikiService documentWikiService = mock(DocumentWikiService.class);
    private final WikiDocumentImportService importService = mock(WikiDocumentImportService.class);
    private final WikiCacheManager wikiCacheManager = mock(WikiCacheManager.class);

    private final User loginUser = user(7L);

    @Test
    void urlBatchKeepsSuccessfulImportsWhenOtherUrlsFail() throws IOException {
        WebPageFetcher fetcher = url -> {
            if (url.contains("good")) {
                return page(url, "政务服务指南", articleHtml());
            }
            if (url.contains("empty")) {
                return page(url, "空页面", "<html><body><script>boot()</script></body></html>");
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "下载失败，HTTP 404");
        };
        WikiBatchImportServiceImpl service = newService(fetcher);
        prepareSpace();
        when(documentWikiService.save(any())).thenAnswer(invocation -> {
            DocumentWiki documentWiki = invocation.getArgument(0);
            documentWiki.setId(101L);
            return true;
        });

        List<BatchImportItemResult> results = service.importUrls(
                request(11L, null, Arrays.asList("https://example.com/good", "https://example.com/bad",
                        "https://example.com/empty")), loginUser);

        assertEquals(3, results.size());
        assertEquals(BatchImportItemResult.STATUS_SUCCESS, results.get(0).getStatus());
        assertEquals(101L, results.get(0).getDocumentId());
        assertEquals("政务服务指南", results.get(0).getTitle());
        assertEquals(BatchImportItemResult.STATUS_FAILED, results.get(1).getStatus());
        assertTrue(results.get(1).getMessage().contains("下载失败"), results.get(1).getMessage());
        assertNull(results.get(1).getDocumentId());
        assertEquals(BatchImportItemResult.STATUS_FAILED, results.get(2).getStatus());
        assertTrue(results.get(2).getMessage().contains("未能从页面提取有效文本内容"), results.get(2).getMessage());
        verify(documentWikiService, times(1)).save(any());
        verify(wikiCacheManager, times(1)).clearSpace(11L);
    }

    @Test
    void urlImportStoresSourceUrlAndMetadata() throws IOException {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "政策解读", articleHtml()));
        prepareSpace();
        WikiFolder folder = new WikiFolder();
        folder.setId(22L);
        when(wikiFolderService.requireVisibleFolder(22L, 11L, loginUser)).thenReturn(folder);
        when(documentWikiService.save(any())).thenAnswer(invocation -> {
            DocumentWiki documentWiki = invocation.getArgument(0);
            documentWiki.setId(55L);
            return true;
        });

        service.importUrls(request(11L, 22L,
                Collections.singletonList("https://example.com/a/b.html")), loginUser);

        ArgumentCaptor<DocumentWiki> captor = ArgumentCaptor.forClass(DocumentWiki.class);
        verify(documentWikiService).save(captor.capture());
        DocumentWiki saved = captor.getValue();
        assertEquals("政策解读", saved.getTitle());
        assertEquals("markdown", saved.getContentFormat());
        assertEquals("URL", saved.getSourceType());
        assertEquals("https://example.com/a/b.html", saved.getSourceUrl());
        assertEquals(11L, saved.getSpaceId());
        assertEquals(22L, saved.getFolderId());
        assertEquals(7L, saved.getUserId());
        assertEquals(0L, saved.getViewCount());
        assertTrue(saved.getMetadataJson().contains("\"sourceType\":\"URL\""), saved.getMetadataJson());
        assertTrue(saved.getMetadataJson().contains("\"importedAs\":\"markdown\""), saved.getMetadataJson());
        assertTrue(saved.getContent().contains("真正的新闻正文内容"), saved.getContent());
    }

    @Test
    void urlImportFallsBackToUrlSegmentWhenPageHasNoTitle() throws IOException {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "", articleHtml()));
        prepareSpace();
        when(documentWikiService.save(any())).thenAnswer(invocation -> {
            DocumentWiki documentWiki = invocation.getArgument(0);
            documentWiki.setId(56L);
            return true;
        });

        List<BatchImportItemResult> results = service.importUrls(request(11L, null,
                Collections.singletonList("https://example.com/news/2026-report.html")), loginUser);

        assertEquals("2026-report", results.get(0).getTitle());
    }

    @Test
    void urlBatchRejectsEmptyListAndOversizedBatch() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();

        assertEquals("请至少填写一个网页地址", assertThrows(BusinessException.class,
                () -> service.importUrls(request(11L, null, Collections.emptyList()), loginUser)).getMessage());

        List<String> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tooMany.add("https://example.com/page-" + i);
        }
        assertTrue(assertThrows(BusinessException.class,
                () -> service.importUrls(request(11L, null, tooMany), loginUser))
                .getMessage().contains("一次最多导入"));
    }

    @Test
    void urlBatchPropagatesSpacePermissionFailure() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        when(wikiSpaceService.requireEditableSpace(11L, loginUser))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限"));

        assertThrows(BusinessException.class,
                () -> service.importUrls(request(11L, null,
                        Collections.singletonList("https://example.com/a")), loginUser));
        verify(documentWikiService, never()).save(any());
    }

    @Test
    void fileBatchImportsValidFilesAndReportsInvalidOnes() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();
        MockMultipartFile valid = file("方案.md", "# 标题\n\n正文");
        MockMultipartFile invalid = file("notes.txt", "plain text");
        when(importService.parse(eq(valid), any())).thenReturn(imported("方案", "# 标题\n\n正文"));
        when(importService.parse(eq(invalid), any()))
                .thenThrow(new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 md、html、htm 文件"));
        when(documentWikiService.save(any())).thenAnswer(invocation -> {
            DocumentWiki documentWiki = invocation.getArgument(0);
            documentWiki.setId(77L);
            return true;
        });

        List<BatchImportItemResult> results = service.importFiles(11L, null,
                Arrays.asList(valid, invalid), loginUser);

        assertEquals(2, results.size());
        assertEquals(BatchImportItemResult.STATUS_SUCCESS, results.get(0).getStatus());
        assertEquals(77L, results.get(0).getDocumentId());
        assertEquals("方案.md", results.get(0).getInput());
        assertEquals(BatchImportItemResult.STATUS_FAILED, results.get(1).getStatus());
        assertTrue(results.get(1).getMessage().contains("仅支持"), results.get(1).getMessage());
        verify(wikiCacheManager, times(1)).clearSpace(11L);
    }

    @Test
    void fileBatchRejectsEmptySelection() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();

        assertEquals("请至少选择一个文件", assertThrows(BusinessException.class,
                () -> service.importFiles(11L, null, Collections.emptyList(), loginUser)).getMessage());
    }

    @Test
    void fileBatchRejectsFolderFromAnotherSpace() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();
        when(wikiFolderService.requireVisibleFolder(22L, 11L, loginUser))
                .thenThrow(new BusinessException(ErrorCode.PARAMS_ERROR, "文件夹不属于目标空间"));

        assertThrows(BusinessException.class, () -> service.importFiles(11L, 22L,
                Collections.singletonList(file("方案.md", "正文")), loginUser));
        verify(documentWikiService, never()).save(any());
    }

    private WikiBatchImportServiceImpl newService(WebPageFetcher fetcher) {
        WikiBatchImportServiceImpl service = new WikiBatchImportServiceImpl();
        ReflectionTestUtils.setField(service, "wikiSpaceService", wikiSpaceService);
        ReflectionTestUtils.setField(service, "wikiFolderService", wikiFolderService);
        ReflectionTestUtils.setField(service, "documentWikiService", documentWikiService);
        ReflectionTestUtils.setField(service, "wikiDocumentImportService", importService);
        ReflectionTestUtils.setField(service, "wikiCacheManager", wikiCacheManager);
        ReflectionTestUtils.setField(service, "webPageFetcher", fetcher);
        ReflectionTestUtils.setField(service, "maxItems", 20);
        return service;
    }

    private void prepareSpace() {
        WikiSpace wikiSpace = new WikiSpace();
        wikiSpace.setId(11L);
        when(wikiSpaceService.requireEditableSpace(11L, loginUser)).thenReturn(wikiSpace);
    }

    private DocumentWikiBatchUrlImportRequest request(Long spaceId, Long folderId, List<String> urls) {
        DocumentWikiBatchUrlImportRequest request = new DocumentWikiBatchUrlImportRequest();
        request.setSpaceId(spaceId);
        request.setFolderId(folderId);
        request.setUrls(urls);
        return request;
    }

    private FetchedPage page(String url, String title, String html) {
        FetchedPage page = new FetchedPage();
        page.setHtml(html);
        page.setFinalUrl(url);
        page.setPageTitle(title);
        return page;
    }

    private String articleHtml() {
        return "<html><head><title>标题</title></head><body><article>"
                + "<p>真正的新闻正文内容，这里需要有足够的长度才能满足已知容器的最小长度要求。</p>"
                + "<p>第二段正文内容，用来确认清洗之后依然保留完整的文章结构信息。</p>"
                + "</article><div class=\"share\">分享到朋友圈</div></body></html>";
    }

    private ImportedWikiDocument imported(String title, String content) {
        ImportedWikiDocument imported = new ImportedWikiDocument();
        imported.setTitle(title);
        imported.setContent(content);
        imported.setContentFormat("markdown");
        imported.setSourceType("UPLOAD");
        return imported;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private MockMultipartFile file(String filename, String content) {
        return new MockMultipartFile("files", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }
}

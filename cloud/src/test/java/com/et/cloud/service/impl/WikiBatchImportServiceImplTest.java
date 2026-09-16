package com.et.cloud.service.impl;

import cn.hutool.core.util.StrUtil;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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

    @Test
    void jsonImportCreatesOneDocumentPerEntry() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();
        stubSavingFrom(101L);

        List<BatchImportItemResult> results = service.importJson(11L, null,
                jsonFile("[{\"title\":\"A\",\"content\":\"one\"},{\"title\":\"B\",\"content\":\"two\"},"
                        + "{\"title\":\"C\",\"content\":\"three\"}]"), loginUser);

        assertEquals(3, results.size(), "one result per entry");
        assertEquals(3, successCount(results));
        assertEquals(101L, results.get(0).getDocumentId());
        assertEquals(103L, results.get(2).getDocumentId());
        verify(documentWikiService, times(3)).save(any());
    }

    @Test
    void jsonImportIdentifiesEachEntryByUrlFallingBackToTitle() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();
        stubSavingFrom(201L);

        List<BatchImportItemResult> results = service.importJson(11L, null,
                jsonFile("[{\"title\":\"A\",\"content\":\"one\",\"url\":\"https://news.test/a\"},"
                        + "{\"title\":\"B\",\"content\":\"two\"}]"), loginUser);

        assertEquals("https://news.test/a", results.get(0).getInput());
        assertEquals("B", results.get(1).getInput());
        assertEquals("A", results.get(0).getTitle());
    }

    @Test
    void jsonImportKeepsOtherEntriesWhenOneHasNoContent() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();
        stubSavingFrom(301L);

        List<BatchImportItemResult> results = service.importJson(11L, null,
                jsonFile("[{\"title\":\"A\",\"content\":\"one\"},{\"title\":\"B\"},"
                        + "{\"title\":\"C\",\"content\":\"three\"}]"), loginUser);

        assertEquals(3, results.size());
        assertEquals(BatchImportItemResult.STATUS_SUCCESS, results.get(0).getStatus());
        assertEquals(BatchImportItemResult.STATUS_FAILED, results.get(1).getStatus());
        assertTrue(results.get(1).getMessage().contains("正文不能为空"), results.get(1).getMessage());
        assertNull(results.get(1).getDocumentId());
        assertEquals(BatchImportItemResult.STATUS_SUCCESS, results.get(2).getStatus());
        verify(documentWikiService, times(2)).save(any());
    }

    @Test
    void jsonImportProcessesMoreEntriesThanTheMultiFileBatchLimit() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();
        stubSavingFrom(401L);

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 25; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"title\":\"doc ").append(i).append("\",\"content\":\"entry ").append(i).append("\"}");
        }
        json.append(']');

        List<BatchImportItemResult> results = service.importJson(11L, null, jsonFile(json.toString()), loginUser);

        assertEquals(25, results.size(), "the json entry point must not apply the batch item limit");
        assertEquals(25, successCount(results));
        verify(documentWikiService, times(25)).save(any());
    }

    @Test
    void jsonImportStoresSourceUrlMetadataAndLanguage() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();
        stubSavingFrom(501L);

        service.importJson(11L, null,
                jsonFile("[{\"title\":\"News\",\"body\":\"The committee said the measure would take effect.\","
                        + "\"url\":\"https://news.test/a\",\"source\":\"mashable\",\"published_at\":\"2023-06-01\"}]"),
                loginUser);

        ArgumentCaptor<DocumentWiki> captor = ArgumentCaptor.forClass(DocumentWiki.class);
        verify(documentWikiService).save(captor.capture());
        DocumentWiki saved = captor.getValue();
        assertEquals("https://news.test/a", saved.getSourceUrl());
        assertEquals("markdown", saved.getContentFormat());
        assertEquals("IMPORT", saved.getSourceType());
        assertEquals(11L, saved.getSpaceId());
        assertEquals(7L, saved.getUserId());
        assertTrue(saved.getMetadataJson().contains("\"source\":\"mashable\""), saved.getMetadataJson());
        assertTrue(saved.getMetadataJson().contains("\"language\":\"en\""),
                "the language must be stored or a rebuild could re-detect a different profile");
    }

    @Test
    void jsonImportRejectsInvalidJsonWithoutCreatingDocuments() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();

        assertThrows(BusinessException.class, () -> service.importJson(11L, null,
                jsonFile("[{\"title\":\"A\",\"content\":\"one\"},{\"title\":\"B\""), loginUser));
        verify(documentWikiService, never()).save(any());
    }

    @Test
    void jsonImportRejectsAnEmptyEntrySetWithoutCreatingDocuments() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();

        assertEquals("JSON 文件中没有可导入的条目", assertThrows(BusinessException.class,
                () -> service.importJson(11L, null, jsonFile("[]"), loginUser)).getMessage());
        verify(documentWikiService, never()).save(any());
    }

    @Test
    void jsonImportRejectsAnOversizedFileBeforeTouchingTheSpace() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();
        MockMultipartFile oversized = new MockMultipartFile("file", "corpus.json", "application/json",
                "[]".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public long getSize() {
                return 30L * 1024 * 1024 + 1;
            }
        };

        assertTrue(assertThrows(BusinessException.class,
                () -> service.importJson(11L, null, oversized, loginUser))
                .getMessage().contains("不能超过 30M"));
        verify(documentWikiService, never()).save(any());
    }

    @Test
    void jsonImportRejectsAnEmptyFile() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();

        assertEquals("请选择要导入的 JSON 文件", assertThrows(BusinessException.class,
                () -> service.importJson(11L, null,
                        new MockMultipartFile("file", "corpus.json", "application/json", new byte[0]), loginUser))
                .getMessage());
    }

    @Test
    void jsonImportPropagatesSpacePermissionFailure() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        when(wikiSpaceService.requireEditableSpace(11L, loginUser))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限"));

        assertThrows(BusinessException.class, () -> service.importJson(11L, null,
                jsonFile("[{\"title\":\"A\",\"content\":\"one\"}]"), loginUser));
        verify(documentWikiService, never()).save(any());
    }

    @Test
    void jsonImportResolvesTheOptionalFolder() {
        WikiBatchImportServiceImpl service = newService(url -> page(url, "标题", articleHtml()));
        prepareSpace();
        WikiFolder folder = new WikiFolder();
        folder.setId(22L);
        when(wikiFolderService.requireVisibleFolder(22L, 11L, loginUser)).thenReturn(folder);
        stubSavingFrom(601L);

        service.importJson(11L, 22L, jsonFile("[{\"title\":\"A\",\"content\":\"one\"}]"), loginUser);

        ArgumentCaptor<DocumentWiki> captor = ArgumentCaptor.forClass(DocumentWiki.class);
        verify(documentWikiService).save(captor.capture());
        assertEquals(22L, captor.getValue().getFolderId());
    }

    private long successCount(List<BatchImportItemResult> results) {
        return results.stream().filter(item -> BatchImportItemResult.STATUS_SUCCESS.equals(item.getStatus())).count();
    }

    /**
     * Stubs the document service the way the real one behaves: it validates before inserting, so a
     * blank title or content fails that entry rather than the whole import.
     */
    private void stubSavingFrom(long firstId) {
        doAnswer(invocation -> {
            DocumentWiki documentWiki = invocation.getArgument(0);
            if (StrUtil.isBlank(documentWiki.getTitle())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题不能为空");
            }
            if (StrUtil.isBlank(documentWiki.getContent())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "正文不能为空");
            }
            return null;
        }).when(documentWikiService).validDocumentWiki(any());
        AtomicLong nextId = new AtomicLong(firstId);
        when(documentWikiService.save(any())).thenAnswer(invocation -> {
            DocumentWiki documentWiki = invocation.getArgument(0);
            documentWiki.setId(nextId.getAndIncrement());
            return true;
        });
    }

    private MockMultipartFile jsonFile(String content) {
        return new MockMultipartFile("file", "corpus.json", "application/json",
                content.getBytes(StandardCharsets.UTF_8));
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

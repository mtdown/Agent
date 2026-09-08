package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.dto.documentWiki.DocumentWikiBatchUrlImportRequest;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.model.dto.BatchImportItemResult;
import com.et.cloud.model.entity.User;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiBatchImportService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Batch endpoints: they only resolve the caller and delegate, returning per-item results.
 */
class DocumentWikiBatchControllerTest {

    private final UserService userService = mock(UserService.class);
    private final WikiBatchImportService batchImportService = mock(WikiBatchImportService.class);

    @Test
    void urlImportReturnsPerItemResults() {
        DocumentWikiBatchController controller = newController();
        User loginUser = user(7L);
        DocumentWikiBatchUrlImportRequest request = request(11L, null,
                Collections.singletonList("https://example.com/a"));
        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(batchImportService.importUrls(request, loginUser)).thenReturn(
                Collections.singletonList(BatchImportItemResult.success("https://example.com/a", 99L, "标题")));

        BaseResponse<List<BatchImportItemResult>> response =
                controller.importUrls(request, new MockHttpServletRequest());

        assertEquals(1, response.getData().size());
        assertEquals(99L, response.getData().get(0).getDocumentId());
        verify(batchImportService).importUrls(request, loginUser);
    }

    @Test
    void urlImportRequiresSpace() {
        DocumentWikiBatchController controller = newController();

        assertEquals("空间不能为空", assertThrows(BusinessException.class,
                () -> controller.importUrls(request(null, null,
                        Collections.singletonList("https://example.com/a")), new MockHttpServletRequest()))
                .getMessage());
    }

    @Test
    void urlImportRequiresLogin() {
        DocumentWikiBatchController controller = newController();
        when(userService.getLoginUser(any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));

        assertThrows(BusinessException.class, () -> controller.importUrls(
                request(11L, null, Collections.singletonList("https://example.com/a")),
                new MockHttpServletRequest()));
        verify(batchImportService, org.mockito.Mockito.never()).importUrls(any(), any());
    }

    @Test
    void fileImportPassesFilesAndDestination() {
        DocumentWikiBatchController controller = newController();
        User loginUser = user(7L);
        MockMultipartFile[] files = {
                new MockMultipartFile("files", "a.md", "text/markdown", "# 标题".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("files", "b.md", "text/markdown", "# 标题二".getBytes(StandardCharsets.UTF_8))
        };
        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(batchImportService.importFiles(eq(11L), eq(22L), any(), eq(loginUser))).thenReturn(
                Arrays.asList(BatchImportItemResult.success("a.md", 1L, "标题"),
                        BatchImportItemResult.failed("b.md", "仅支持 md、html、htm 文件")));

        BaseResponse<List<BatchImportItemResult>> response =
                controller.importFiles(files, 11L, 22L, new MockHttpServletRequest());

        assertEquals(2, response.getData().size());
        assertEquals("FAILED", response.getData().get(1).getStatus());
        verify(batchImportService).importFiles(eq(11L), eq(22L), any(), eq(loginUser));
    }

    @Test
    void fileImportWithoutFolderPassesNullFolder() {
        DocumentWikiBatchController controller = newController();
        User loginUser = user(7L);
        MockMultipartFile[] files = {
                new MockMultipartFile("files", "a.md", "text/markdown", "# 标题".getBytes(StandardCharsets.UTF_8))
        };
        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(batchImportService.importFiles(eq(11L), isNull(), any(), eq(loginUser)))
                .thenReturn(Collections.singletonList(BatchImportItemResult.success("a.md", 1L, "标题")));

        controller.importFiles(files, 11L, null, new MockHttpServletRequest());

        verify(batchImportService).importFiles(eq(11L), isNull(), any(), eq(loginUser));
    }

    @Test
    void fileImportRequiresAtLeastOneFile() {
        DocumentWikiBatchController controller = newController();

        assertEquals("请至少选择一个文件", assertThrows(BusinessException.class,
                () -> controller.importFiles(new MockMultipartFile[0], 11L, null, new MockHttpServletRequest()))
                .getMessage());
    }

    private DocumentWikiBatchController newController() {
        DocumentWikiBatchController controller = new DocumentWikiBatchController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "wikiBatchImportService", batchImportService);
        return controller;
    }

    private DocumentWikiBatchUrlImportRequest request(Long spaceId, Long folderId, List<String> urls) {
        DocumentWikiBatchUrlImportRequest request = new DocumentWikiBatchUrlImportRequest();
        request.setSpaceId(spaceId);
        request.setFolderId(folderId);
        request.setUrls(urls);
        return request;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}

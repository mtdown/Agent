package com.et.cloud.rag;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.controller.OpenRagController;
import com.et.cloud.controller.RagApiKeyController;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.model.entity.User;
import com.et.cloud.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Thin delegation checks: the open endpoints resolve the owner ONCE from the
 * X-API-Key header and then reuse the permission-filtered services unchanged;
 * key management requires a login user.
 */
class OpenRagControllerTest {

    private RagApiKeyService ragApiKeyService;
    private RagSearchService ragSearchService;
    private RagAskService ragAskService;
    private UserService userService;
    private OpenRagController openController;
    private RagApiKeyController keyController;

    @BeforeEach
    void setUp() {
        ragApiKeyService = mock(RagApiKeyService.class);
        ragSearchService = mock(RagSearchService.class);
        ragAskService = mock(RagAskService.class);
        userService = mock(UserService.class);
        openController = new OpenRagController();
        ReflectionTestUtils.setField(openController, "ragApiKeyService", ragApiKeyService);
        ReflectionTestUtils.setField(openController, "ragSearchService", ragSearchService);
        ReflectionTestUtils.setField(openController, "ragAskService", ragAskService);
        keyController = new RagApiKeyController();
        ReflectionTestUtils.setField(keyController, "userService", userService);
        ReflectionTestUtils.setField(keyController, "ragApiKeyService", ragApiKeyService);
    }

    private User owner() {
        User user = new User();
        user.setId(7L);
        return user;
    }

    @Test
    void searchDelegatesToPermissionFilteredService() {
        when(ragApiKeyService.resolveUser("cpk_valid")).thenReturn(owner());
        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("低空经济");
        RagSearchResult result = new RagSearchResult();
        when(ragSearchService.search(owner(), request)).thenReturn(result);

        BaseResponse<RagSearchResult> response = openController.search(request, "cpk_valid");
        assertEquals(0, response.getCode());
        verify(ragSearchService).search(owner(), request);
    }

    @Test
    void invalidKeyIsRejectedBeforeAnyServiceCall() {
        when(ragApiKeyService.resolveUser("cpk_bad"))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR,
                        RagApiKeyServiceImpl.INVALID_KEY_MESSAGE));
        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("anything");

        BusinessException e = assertThrows(BusinessException.class,
                () -> openController.search(request, "cpk_bad"));
        assertEquals(40101, e.getCode());
    }

    @Test
    void askStreamsViaSameService() {
        when(ragApiKeyService.resolveUser("cpk_valid")).thenReturn(owner());
        RagAskRequest request = new RagAskRequest();
        request.setQuery("低空经济");
        SseEmitter emitter = new SseEmitter();
        when(ragAskService.ask(owner(), request)).thenReturn(emitter);

        SseEmitter returned = openController.ask(request, "cpk_valid");
        assertEquals(emitter, returned);
    }

    @Test
    void keyCreateRequiresLoginUser() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(userService.getLoginUser(httpRequest)).thenReturn(owner());
        when(ragApiKeyService.create(eq(owner()), any())).thenReturn(new RagApiKeyCreatedView());

        RagApiKeyController.KeyCreateRequest req = new RagApiKeyController.KeyCreateRequest();
        req.setKeyName("本地 Agent");
        BaseResponse<RagApiKeyCreatedView> response = keyController.create(req, httpRequest);
        assertEquals(0, response.getCode());
        assertTrue(response.getData() != null);
        verify(ragApiKeyService).create(owner(), "本地 Agent");
    }
}

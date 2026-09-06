package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.vis.DocumentWikiVis;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentWikiControllerCacheTest {

    @Test
    void detailCacheMissRepopulatesContentFormat() {
        DocumentWikiController controller = new DocumentWikiController();
        DocumentWikiService documentWikiService = mock(DocumentWikiService.class);
        UserService userService = mock(UserService.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        ReflectionTestUtils.setField(controller, "documentWikiService", documentWikiService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "stringRedisTemplate", stringRedisTemplate);

        User loginUser = new User();
        loginUser.setId(1L);
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setId(10L);
        documentWiki.setSpaceId(2L);
        DocumentWikiVis documentWikiVis = new DocumentWikiVis();
        documentWikiVis.setId(10L);
        documentWikiVis.setSpaceId(2L);
        documentWikiVis.setContentFormat("markdown");

        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(documentWikiService.getById(10L)).thenReturn(documentWiki);
        doNothing().when(documentWikiService).checkDocumentWikiVisible(loginUser, documentWiki);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("agentWiki:documentWiki:detail:2:10")).thenReturn(null);
        when(documentWikiService.getDocumentWikiVis(eq(documentWiki), any())).thenReturn(documentWikiVis);

        BaseResponse<DocumentWikiVis> response = controller.getDocumentWikiVisById(10L, new MockHttpServletRequest());

        assertEquals("markdown", response.getData().getContentFormat());
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("agentWiki:documentWiki:detail:2:10"), jsonCaptor.capture(), anyLong(),
                eq(TimeUnit.SECONDS));
        assertTrue(jsonCaptor.getValue().contains("\"contentFormat\":\"markdown\""));
    }
}

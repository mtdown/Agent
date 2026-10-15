package com.et.cloud.controller;

import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiSpaceService;
import com.et.cloud.rag.RagSearchService;
import com.et.cloud.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.util.Collections;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentPreviewControllerTest {
    @Test void checksLoginBeforeContactingAgent() {
        UserService users = mock(UserService.class);
        AgentPreviewProxy proxy = mock(AgentPreviewProxy.class);
        AgentConversationStore store = mock(AgentConversationStore.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(users.getLoginUser(request)).thenThrow(new IllegalStateException("not logged in"));
        AgentPreviewController controller = new AgentPreviewController(users, proxy, store,
                mock(RagSearchService.class), mock(WikiSpaceService.class));
        assertThrows(IllegalStateException.class, () -> controller.stream(new AgentPreviewController.Ask("q"), request));
        verifyNoInteractions(proxy);
        verifyNoInteractions(store);
    }

    @Test void delegatesAuthenticatedRequest() {
        UserService users = mock(UserService.class);
        AgentPreviewProxy proxy = mock(AgentPreviewProxy.class);
        AgentConversationStore store = mock(AgentConversationStore.class);
        RagSearchService search = mock(RagSearchService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        User user = new User(); user.setId(1L);
        when(users.getLoginUser(request)).thenReturn(user);
        when(store.recentCompleted(1L, 2L)).thenReturn(Collections.emptyList());
        StreamingResponseBody expected = out -> {};
        when(proxy.stream(anyMap(), anyMap(), any())).thenReturn(expected);
        AgentPreviewController.Ask ask = new AgentPreviewController.Ask("q");
        ask.conversationId = "2";
        assertSame(expected, new AgentPreviewController(users, proxy, store, search, mock(WikiSpaceService.class))
                .stream(ask, request));
        verify(store).requireOwner(1L, 2L);
        verifyNoInteractions(search);
    }

    @Test void selectedUnauthorizedSpacesNeverBecomeAllSpaces() {
        UserService users = mock(UserService.class);
        AgentPreviewProxy proxy = mock(AgentPreviewProxy.class);
        AgentConversationStore store = mock(AgentConversationStore.class);
        RagSearchService search = mock(RagSearchService.class);
        WikiSpaceService spaces = mock(WikiSpaceService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        User user = new User(); user.setId(1L);
        when(users.getLoginUser(request)).thenReturn(user);
        when(spaces.listVisibleSpaceIds(user)).thenReturn(Collections.singletonList(3L));
        when(store.recentCompleted(1L, 2L)).thenReturn(Collections.emptyList());
        AgentPreviewController.Ask ask = new AgentPreviewController.Ask("q");
        ask.conversationId = "2";
        ask.ragMode = "SELECTED";
        ask.spaceIds = Arrays.asList("999");
        new AgentPreviewController(users, proxy, store, search, spaces).stream(ask, request);
        verifyNoInteractions(search);
        verifyNoInteractions(proxy);
    }
}

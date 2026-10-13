package com.et.cloud.controller;

import com.et.cloud.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentPreviewControllerTest {
    @Test void checksLoginBeforeContactingAgent() {
        UserService users = mock(UserService.class);
        AgentPreviewProxy proxy = mock(AgentPreviewProxy.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(users.getLoginUser(request)).thenThrow(new IllegalStateException("not logged in"));
        AgentPreviewController controller = new AgentPreviewController(users, proxy);
        assertThrows(IllegalStateException.class, () -> controller.stream(new AgentPreviewController.Ask("q"), request));
        verifyNoInteractions(proxy);
    }

    @Test void delegatesAuthenticatedRequest() {
        UserService users = mock(UserService.class);
        AgentPreviewProxy proxy = mock(AgentPreviewProxy.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(users.getLoginUser(request)).thenReturn(new com.et.cloud.model.entity.User());
        StreamingResponseBody expected = out -> {};
        when(proxy.stream("q")).thenReturn(expected);
        assertSame(expected, new AgentPreviewController(users, proxy)
                .stream(new AgentPreviewController.Ask("q"), request));
    }
}

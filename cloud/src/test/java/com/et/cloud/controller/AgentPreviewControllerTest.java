package com.et.cloud.controller;

import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiSpaceService;
import com.et.cloud.rag.RagSearchService;
import com.et.cloud.rag.RagSearchRequest;
import com.et.cloud.rag.RagSearchResult;
import com.et.cloud.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import org.mockito.ArgumentCaptor;
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
                mock(RagSearchService.class), mock(WikiSpaceService.class), emptyCatalog());
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
        AgentLibraryStore library = mock(AgentLibraryStore.class);
        AgentLibraryStore.SkillItem item = new AgentLibraryStore.SkillItem();
        item.id = "9007199254740993";
        item.version = 2;
        item.name = "Example";
        item.summary = "Does work";
        AgentLibraryStore.SkillVersion version = new AgentLibraryStore.SkillVersion();
        version.definitionJson = "{\"when_to_use\":\"example request\",\"when_not_to_use\":\"other request\"}";
        when(library.listAvailable(1L)).thenReturn(List.of(item));
        when(library.skillVersion(1L, item.id, 2)).thenReturn(version);
        AgentSkillCatalog catalog = new AgentSkillCatalog(library);
        AgentPreviewController.Ask ask = new AgentPreviewController.Ask("q");
        ask.conversationId = "2";
        assertSame(expected, new AgentPreviewController(users, proxy, store, search, mock(WikiSpaceService.class), catalog)
                .stream(ask, request));
        verify(store).requireOwner(1L, 2L);
        verifyNoInteractions(search);
        ArgumentCaptor<java.util.Map> payload = ArgumentCaptor.forClass(java.util.Map.class);
        verify(proxy).stream(payload.capture(), anyMap(), any());
        assertEquals("1", payload.getValue().get("userId"));
        String brief = (String) payload.getValue().get("skillCatalog");
        assertTrue(brief.contains("9007199254740993@2 | Example"));
        assertTrue(brief.contains("when_to_use: example request"));
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
        new AgentPreviewController(users, proxy, store, search, spaces, emptyCatalog()).stream(ask, request);
        verifyNoInteractions(search);
        verifyNoInteractions(proxy);
    }

    @Test void folderOnlySelectionReachesPermissionFilteredSearch() {
        UserService users = mock(UserService.class);
        AgentPreviewProxy proxy = mock(AgentPreviewProxy.class);
        AgentConversationStore store = mock(AgentConversationStore.class);
        RagSearchService search = mock(RagSearchService.class);
        WikiSpaceService spaces = mock(WikiSpaceService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        User user = new User(); user.setId(1L);
        when(users.getLoginUser(request)).thenReturn(user);
        when(spaces.listVisibleSpaceIds(user)).thenReturn(List.of(1L));
        when(search.search(eq(user), any(RagSearchRequest.class))).thenReturn(new RagSearchResult());
        AgentPreviewController.Ask ask = new AgentPreviewController.Ask("q");
        ask.conversationId = "2";
        ask.ragMode = "SELECTED";
        ask.folderIds = List.of("123");

        new AgentPreviewController(users, proxy, store, search, spaces, emptyCatalog()).stream(ask, request);

        ArgumentCaptor<RagSearchRequest> captured = ArgumentCaptor.forClass(RagSearchRequest.class);
        verify(search).search(eq(user), captured.capture());
        assertEquals(List.of(123L), captured.getValue().getFolderIds());
        assertEquals(List.of(), captured.getValue().getSpaceIds());
    }

    private static AgentSkillCatalog emptyCatalog() {
        return new AgentSkillCatalog(mock(AgentLibraryStore.class));
    }
}

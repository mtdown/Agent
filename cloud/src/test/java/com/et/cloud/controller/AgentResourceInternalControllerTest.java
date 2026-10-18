package com.et.cloud.controller;

import com.et.cloud.exception.GlobalExceptionHandler;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiCacheManager;
import com.et.cloud.service.WikiSpaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class AgentResourceInternalControllerTest {
    @TempDir Path root;

    @Test void writesAndReadsUserFileOnlyWithAgentCredential() throws Exception {
        AgentPersonalFileStore files = new AgentPersonalFileStore(root);
        AgentResourceInternalController controller = new AgentResourceInternalController(
                files, writer(mock(UserService.class), mock(WikiSpaceService.class), mock(DocumentWikiService.class)), "secret");
        assertThrows(ResponseStatusException.class, () -> controller.readFile("bad", "7", "notes/a.txt"));
        var mvc = MockMvcBuilders.standaloneSetup(controller, new GlobalExceptionHandler()).build();
        mvc.perform(post("/internal/agent/files").param("userId", "7")
                        .header("X-Agent-Key", "secret").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"notes/a.txt\",\"content\":\"saved\",\"mode\":\"create\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.verified").value(true));
        mvc.perform(get("/internal/agent/files").param("userId", "7").param("path", "notes/a.txt")
                        .header("X-Agent-Key", "secret"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("saved"));
        mvc.perform(get("/internal/agent/files").param("userId", "8").param("path", "notes/a.txt")
                        .header("X-Agent-Key", "secret"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40000));
    }

    @Test void knowledgeWriteDelegatesCurrentUserAndRequiresCredential() throws Exception {
        UserService users = mock(UserService.class);
        WikiSpaceService spaces = mock(WikiSpaceService.class);
        DocumentWikiService documents = mock(DocumentWikiService.class);
        User user = new User(); user.setId(7L);
        WikiSpace space = new WikiSpace(); space.setId(12L);
        when(users.getById(7L)).thenReturn(user);
        when(spaces.requireEditableSpace(12L, user)).thenReturn(space);
        when(documents.save(any(DocumentWiki.class))).thenAnswer(invocation -> {
            ((DocumentWiki) invocation.getArgument(0)).setId(9007199254740993L);
            return true;
        });
        AgentWikiDocumentWriter writer = writer(users, spaces, documents);
        AgentResourceInternalController controller = new AgentResourceInternalController(
                new AgentPersonalFileStore(root), writer, "secret");
        assertThrows(ResponseStatusException.class, () -> controller.writeWikiDocument("bad", "7", new AgentResourceInternalController.WikiDocumentRequest()));
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/internal/agent/wiki-documents").param("userId", "7")
                        .header("X-Agent-Key", "secret").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceId\":\"12\",\"title\":\"Title\",\"content\":\"Body\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("9007199254740993"));
        verify(spaces).requireEditableSpace(12L, user);
        verify(documents).save(any(DocumentWiki.class));
    }

    private static AgentWikiDocumentWriter writer(UserService users, WikiSpaceService spaces,
                                                  DocumentWikiService documents) {
        return new AgentWikiDocumentWriter(users, spaces, documents, mock(WikiCacheManager.class));
    }
}

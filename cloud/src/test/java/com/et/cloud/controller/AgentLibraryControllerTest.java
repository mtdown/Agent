package com.et.cloud.controller;

import com.et.cloud.model.entity.User;
import com.et.cloud.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLibraryControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void skillCreationUsesAuthenticatedOwnerNotClientField() throws Exception {
        UserService users = mock(UserService.class);
        AgentLibraryStore store = mock(AgentLibraryStore.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        User user = new User();
        user.setId(7L);
        when(users.getLoginUser(request)).thenReturn(user);
        when(store.createSkill(eq(7L), anyString())).thenReturn("9007199254740993");

        AgentLibraryController controller = new AgentLibraryController(users, store);
        assertEquals("9007199254740993", controller.createSkill(JSON.readTree("{\"ownerUserId\":99,\"name\":\"x\"}"), request).getData());
        verify(store).createSkill(7L, "{\"ownerUserId\":99,\"name\":\"x\"}");
    }

    @Test
    void codePublicationUsesAuthenticatedOwner() {
        UserService users = mock(UserService.class);
        AgentLibraryStore store = mock(AgentLibraryStore.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        User user = new User();
        user.setId(8L);
        when(users.getLoginUser(request)).thenReturn(user);
        AgentLibraryController.CodeRequest body = new AgentLibraryController.CodeRequest();
        body.language = "PYTHON";
        body.entrypoint = "run";
        body.sourceCode = "pass";
        body.inputSchemaJson = "{}";
        body.outputSchemaJson = "{}";

        new AgentLibraryController(users, store).publishCode("9007199254740993", body, request);
        verify(store).publishCode(8L, "9007199254740993", "PYTHON", "run", "pass", "{}", "{}");
    }
}

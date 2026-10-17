package com.et.cloud.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentLibraryInternalControllerTest {
    @Test
    void rejectsMissingInternalCredentialAndReadsExactEnabledVersion() {
        AgentLibraryStore store = mock(AgentLibraryStore.class);
        AgentLibraryInternalController controller = new AgentLibraryInternalController(store, "secret");
        assertThrows(ResponseStatusException.class,
                () -> controller.getSkill("wrong", "7", "9007199254740993", 2));
        controller.getSkill("secret", "7", "9007199254740993", 2);
        verify(store).enabledSkillVersion(7L, "9007199254740993", 2);
    }

    @Test
    void createsCodeAndSkillForAuthenticatedRequestUserOnly() {
        AgentLibraryStore store = mock(AgentLibraryStore.class);
        AgentLibraryInternalController controller = new AgentLibraryInternalController(store, "secret");
        AgentLibraryController.CodeRequest code = new AgentLibraryController.CodeRequest();
        code.name = "Compute";
        code.summary = "Computes";
        code.language = "PYTHON";
        code.entrypoint = "run";
        code.sourceCode = "def run(inputs, resources): return {}";
        code.inputSchemaJson = "{\"type\":\"object\",\"properties\":{}}";
        code.outputSchemaJson = code.inputSchemaJson;
        when(store.createCode(7L, code.name, code.summary, code.language, code.entrypoint, code.sourceCode,
                code.inputSchemaJson, code.outputSchemaJson)).thenReturn("9007199254740994");
        assertThrows(ResponseStatusException.class, () -> controller.createCode("wrong", "7", code));
        assertEquals("9007199254740994", controller.createCode("secret", "7", code).getData());

        String definition = "{\"name\":\"Compute\"}";
        when(store.createSkill(7L, definition)).thenReturn("9007199254740993");
        assertEquals("9007199254740993", controller.createSkill("secret", "7", definition).getData());
        verify(store).createSkill(7L, definition);
    }

    @Test
    void acceptsSkillDefinitionAsJsonRequestBody() throws Exception {
        AgentLibraryStore store = mock(AgentLibraryStore.class);
        AgentLibraryInternalController controller = new AgentLibraryInternalController(store, "secret");
        String definition = "{\"name\":\"Demo\"}";
        when(store.createSkill(7L, definition)).thenReturn("9007199254740993");
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/internal/agent/skills").param("userId", "7")
                        .header("X-Agent-Key", "secret")
                        .contentType(MediaType.APPLICATION_JSON).content(definition))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("9007199254740993"));
    }

    @Test
    void returnsFieldValidationErrorForAgentSkillDraft() throws Exception {
        AgentLibraryStore store = mock(AgentLibraryStore.class);
        String definition = "{\"parameters\":[{\"description\":\"extra\"}]}";
        doThrow(new IllegalArgumentException("Unknown field: parameters[0].description"))
                .when(store).createSkill(7L, definition);
        MockMvcBuilders.standaloneSetup(new AgentLibraryInternalController(store, "secret"),
                        new com.et.cloud.exception.GlobalExceptionHandler()).build()
                .perform(post("/internal/agent/skills").param("userId", "7")
                        .header("X-Agent-Key", "secret")
                        .contentType(MediaType.APPLICATION_JSON).content(definition))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Unknown field: parameters[0].description"));
    }
}

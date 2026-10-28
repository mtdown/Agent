package com.et.cloud.controller;

import com.et.cloud.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
                files, "secret");
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

}

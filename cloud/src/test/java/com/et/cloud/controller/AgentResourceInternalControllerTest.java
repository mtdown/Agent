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

    @Test void segmentContinuationIsAuthenticatedAndVersionBound() throws Exception {
        AgentPersonalFileStore files = new AgentPersonalFileStore(root);
        files.write(7, "a.txt", "中".repeat(900), "create");
        files.write(8, "a.txt", "private", "create");
        var mvc = MockMvcBuilders.standaloneSetup(new AgentResourceInternalController(files, "secret"), new GlobalExceptionHandler()).build();
        String first = mvc.perform(get("/internal/agent/files/segment").param("userId", "7").param("path", "a.txt").header("X-Agent-Key", "secret"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        String cursor = new com.fasterxml.jackson.databind.ObjectMapper().readTree(first).path("data").path("next").asText();
        org.junit.jupiter.api.Assertions.assertFalse(cursor.isEmpty());
        mvc.perform(get("/internal/agent/files/segment").param("userId", "8").param("path", "a.txt").param("cursor", cursor).header("X-Agent-Key", "secret"))
                .andExpect(jsonPath("$.code").value(40000));
        mvc.perform(get("/internal/agent/files/segment").param("userId", "7").param("path", "a.txt").param("cursor", cursor + "x").header("X-Agent-Key", "secret"))
                .andExpect(jsonPath("$.code").value(40000));
        files.write(7, "a.txt", "changed", "overwrite");
        mvc.perform(get("/internal/agent/files/segment").param("userId", "7").param("path", "a.txt").param("cursor", cursor).header("X-Agent-Key", "secret"))
                .andExpect(jsonPath("$.code").value(40000)).andExpect(jsonPath("$.message").value("Personal file version conflict"));
    }

    @Test void searchAndLegacyReadRespectLimitsAndRestart() throws Exception {
        AgentPersonalFileStore files = new AgentPersonalFileStore(root);
        files.write(7, "a.txt", "x".repeat(4100) + "中文" + "z".repeat(5000), "create");
        var mvc = MockMvcBuilders.standaloneSetup(new AgentResourceInternalController(files, "secret"), new GlobalExceptionHandler()).build();
        mvc.perform(get("/internal/agent/files").param("userId", "7").param("path", "a.txt").header("X-Agent-Key", "secret"))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("read_file_segment")))
                .andExpect(jsonPath("$.data.content").doesNotExist());
        String first = mvc.perform(get("/internal/agent/files/search").param("userId", "7").param("path", "a.txt").param("query", "中文").header("X-Agent-Key", "secret"))
                .andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        String cursor = new com.fasterxml.jackson.databind.ObjectMapper().readTree(first).path("data").path("next").asText();
        var restarted = MockMvcBuilders.standaloneSetup(new AgentResourceInternalController(new AgentPersonalFileStore(root), "secret"), new GlobalExceptionHandler()).build();
        restarted.perform(get("/internal/agent/files/search").param("userId", "7").param("path", "a.txt").param("query", "中文").param("cursor", cursor).header("X-Agent-Key", "secret"))
                .andExpect(jsonPath("$.data.offsets[0]").value(4100));
        restarted.perform(get("/internal/agent/files/search").param("userId", "7").param("path", "a.txt").param("query", "other").param("cursor", cursor).header("X-Agent-Key", "secret"))
                .andExpect(jsonPath("$.code").value(40000));
        var rotated = MockMvcBuilders.standaloneSetup(new AgentResourceInternalController(files, "rotated"), new GlobalExceptionHandler()).build();
        rotated.perform(get("/internal/agent/files/search").param("userId", "7").param("path", "a.txt").param("query", "中文").param("cursor", cursor).header("X-Agent-Key", "rotated"))
                .andExpect(jsonPath("$.code").value(40000));
        assertThrows(ResponseStatusException.class, () -> new AgentResourceInternalController(files, "secret").readSegment("bad", "7", "a.txt", null));
    }

}

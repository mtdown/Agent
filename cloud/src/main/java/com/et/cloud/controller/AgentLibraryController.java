package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/** Authenticated library APIs; all storage operations use the current session owner. */
@RestController
@RequestMapping("/agent/library")
public class AgentLibraryController {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final UserService users;
    private final AgentLibraryStore store;

    public AgentLibraryController(UserService users, AgentLibraryStore store) {
        this.users = users;
        this.store = store;
    }

    public static final class CodeRequest {
        public String name;
        public String summary;
        public String language;
        public String entrypoint;
        public String sourceCode;
        public String inputSchemaJson;
        public String outputSchemaJson;
    }

    public static final class VersionRequest { public int version; }
    public static final class ShareRequest { public String targetUserId; }

    @GetMapping("/skills")
    public BaseResponse<List<AgentLibraryStore.SkillItem>> skills(HttpServletRequest request) {
        return ResultUtils.success(store.listSkills(owner(request)));
    }

    @GetMapping("/skills/{id}/versions/{version}")
    public BaseResponse<AgentLibraryStore.SkillVersion> skillVersion(
            @PathVariable String id, @PathVariable int version, HttpServletRequest request) {
        return ResultUtils.success(store.skillVersion(owner(request), id, version));
    }

    @PostMapping("/skills")
    public BaseResponse<String> createSkill(@RequestBody JsonNode definition, HttpServletRequest request) {
        return ResultUtils.success(store.createSkill(owner(request), json(definition)));
    }

    @PostMapping("/skills/{id}/versions")
    public BaseResponse<Integer> reviseSkill(@PathVariable String id, @RequestBody JsonNode definition,
                                              HttpServletRequest request) {
        return ResultUtils.success(store.reviseSkill(owner(request), id, json(definition)));
    }

    @PostMapping("/skills/{id}/enable")
    public BaseResponse<Boolean> enableSkill(@PathVariable String id, @RequestBody VersionRequest body,
                                              HttpServletRequest request) {
        if (body == null || body.version < 1) throw new IllegalArgumentException("Invalid Skill version");
        store.enableSkill(owner(request), id, body.version);
        return ResultUtils.success(true);
    }

    @PostMapping("/skills/{id}/disable")
    public BaseResponse<Boolean> disableSkill(@PathVariable String id, HttpServletRequest request) {
        store.disableSkill(owner(request), id);
        return ResultUtils.success(true);
    }

    @PostMapping("/skills/{id}/share")
    public BaseResponse<Boolean> shareSkill(@PathVariable String id, @RequestBody ShareRequest body,
                                             HttpServletRequest request) {
        if (body == null) throw new IllegalArgumentException("Missing sharing target");
        store.shareSkill(owner(request), id, Long.parseLong(body.targetUserId));
        return ResultUtils.success(true);
    }

    @GetMapping("/codes")
    public BaseResponse<List<AgentLibraryStore.CodeItem>> codes(HttpServletRequest request) {
        return ResultUtils.success(store.listCodes(owner(request)));
    }

    @GetMapping("/codes/{id}/versions/{version}")
    public BaseResponse<AgentLibraryStore.CodeVersion> codeVersion(
            @PathVariable String id, @PathVariable int version, HttpServletRequest request) {
        return ResultUtils.success(store.codeVersion(owner(request), id, version));
    }

    @PostMapping("/codes")
    public BaseResponse<String> createCode(@RequestBody CodeRequest body, HttpServletRequest request) {
        if (body == null) throw new IllegalArgumentException("Missing code item");
        return ResultUtils.success(store.createCode(owner(request), body.name, body.summary, body.language,
                body.entrypoint, body.sourceCode, body.inputSchemaJson, body.outputSchemaJson));
    }

    @PostMapping("/codes/{id}/versions")
    public BaseResponse<Integer> publishCode(@PathVariable String id, @RequestBody CodeRequest body,
                                              HttpServletRequest request) {
        if (body == null) throw new IllegalArgumentException("Missing code version");
        return ResultUtils.success(store.publishCode(owner(request), id, body.language, body.entrypoint,
                body.sourceCode, body.inputSchemaJson, body.outputSchemaJson));
    }

    @PostMapping("/codes/{id}/share")
    public BaseResponse<Boolean> shareCode(@PathVariable String id, @RequestBody ShareRequest body,
                                            HttpServletRequest request) {
        if (body == null) throw new IllegalArgumentException("Missing sharing target");
        store.shareCode(owner(request), id, Long.parseLong(body.targetUserId));
        return ResultUtils.success(true);
    }

    private long owner(HttpServletRequest request) { return users.getLoginUser(request).getId(); }

    private static String json(JsonNode value) {
        if (value == null) throw new IllegalArgumentException("Missing Skill definition");
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid Skill definition", e); }
    }
}

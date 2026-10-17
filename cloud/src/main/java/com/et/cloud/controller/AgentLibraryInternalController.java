package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Internal Agent lookup. The Cloud service remains the permission authority. */
@RestController
@RequestMapping("/internal/agent")
public final class AgentLibraryInternalController {
    private final AgentLibraryStore store;
    private final String secret;

    public AgentLibraryInternalController(AgentLibraryStore store,
                                          @Value("${agent.internal-key:}") String secret) {
        this.store = store;
        this.secret = secret;
    }

    @GetMapping("/skills/{id}/versions/{version}")
    public BaseResponse<AgentLibraryStore.SkillVersion> getSkill(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId, @PathVariable String id, @PathVariable int version) {
        long user = requireUser(key, userId);
        if (version < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return ResultUtils.success(store.enabledSkillVersion(user, id, version));
    }

    @PostMapping("/skills")
    public BaseResponse<String> createSkill(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId, @RequestBody String definition) {
        long user = requireUser(key, userId);
        try {
            return ResultUtils.success(store.createSkill(user, definition));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, e.getMessage());
        }
    }

    @PostMapping("/codes")
    public BaseResponse<String> createCode(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId, @RequestBody AgentLibraryController.CodeRequest body) {
        long user = requireUser(key, userId);
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return ResultUtils.success(store.createCode(user, body.name, body.summary, body.language,
                body.entrypoint, body.sourceCode, body.inputSchemaJson, body.outputSchemaJson));
    }

    private long requireUser(String key, String userId) {
        if (secret == null || secret.isEmpty()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        if (key == null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        long user;
        try { user = Long.parseLong(userId); }
        catch (NumberFormatException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        if (user < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return user;
    }
}

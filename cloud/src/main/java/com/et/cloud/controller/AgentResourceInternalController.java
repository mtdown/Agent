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
import java.util.Map;

/** User-scoped file and knowledge operations reachable only through the Agent key. */
@RestController
@RequestMapping("/internal/agent")
public final class AgentResourceInternalController {
    private final AgentPersonalFileStore files;
    private final AgentWikiDocumentWriter wiki;
    private final String secret;

    public static final class WriteFileRequest {
        public String path;
        public String content;
        public String mode;
    }

    public static final class WikiDocumentRequest {
        public String spaceId;
        public String title;
        public String content;
    }

    public AgentResourceInternalController(AgentPersonalFileStore files, AgentWikiDocumentWriter wiki,
                                           @Value("${agent.internal-key:}") String secret) {
        this.files = files; this.wiki = wiki; this.secret = secret;
    }

    @PostMapping("/files")
    public BaseResponse<AgentPersonalFileStore.WriteResult> writeFile(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId, @RequestBody WriteFileRequest body) {
        long user = requireUser(key, userId);
        if (body == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "Missing file request");
        try { return ResultUtils.success(files.write(user, body.path, body.content, body.mode)); }
        catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.PARAMS_ERROR, e.getMessage()); }
    }

    @GetMapping("/files")
    public BaseResponse<Map<String, String>> readFile(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId, @RequestParam String path) {
        long user = requireUser(key, userId);
        try { return ResultUtils.success(Map.of("path", path, "content", files.read(user, path))); }
        catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.PARAMS_ERROR, e.getMessage()); }
    }

    @PostMapping("/wiki-documents")
    public BaseResponse<String> writeWikiDocument(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId, @RequestBody WikiDocumentRequest body) {
        long user = requireUser(key, userId);
        if (body == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "Missing knowledge document");
        try { return ResultUtils.success(wiki.create(user, body.spaceId, body.title, body.content)); }
        catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.PARAMS_ERROR, e.getMessage()); }
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

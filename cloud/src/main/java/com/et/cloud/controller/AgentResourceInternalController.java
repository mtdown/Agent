package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.service.impl.AgentWikiCreationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import javax.annotation.Resource;

/** User-scoped file and knowledge operations reachable only through the Agent key. */
@RestController
@RequestMapping("/internal/agent")
public final class AgentResourceInternalController {
    private final AgentPersonalFileStore files;
    private final String secret;

    @Resource
    private AgentWikiCreationService creation;

    @Resource
    private AgentWritableWikiLocations writableWikiLocations;

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

    public static final class WikiFolderRequest {
        public String spaceId;
        public String taskName;
    }

    public static final class WikiCreateDocumentRequest {
        public String spaceId;
        public String folderId;
        public String title;
        public String content;
    }

    public AgentResourceInternalController(AgentPersonalFileStore files,
                                           @Value("${agent.internal-key:}") String secret) {
        this.files = files; this.secret = secret;
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
    public BaseResponse<AgentWikiCreationService.DocumentResult> writeWikiDocument(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId, @RequestBody WikiDocumentRequest body) {
        long user = requireUser(key, userId);
        if (body == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "Missing knowledge document");
        try { return ResultUtils.success(creation.createLegacyDocument(user, body.spaceId, body.title, body.content)); }
        catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.PARAMS_ERROR, e.getMessage()); }
    }

    @GetMapping("/wiki-locations")
    public BaseResponse<Map<String, Object>> listWritableWikiLocations(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId,
            @RequestParam(required = false) String spaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        long user = requireUser(key, userId);
        return ResultUtils.success(writableWikiLocations.list(user, spaceId, cursor, limit));
    }

    @PostMapping("/wiki-folders")
    public BaseResponse<AgentWikiCreationService.FolderResult> createWikiFolder(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId, @RequestBody WikiFolderRequest body) {
        long user = requireUser(key, userId);
        if (body == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "缺少任务文件夹请求");
        return ResultUtils.success(creation.createFolder(user, body.spaceId, body.taskName));
    }

    @PostMapping("/wiki-documents/create")
    public BaseResponse<AgentWikiCreationService.DocumentResult> createWikiDocument(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId, @RequestBody WikiCreateDocumentRequest body) {
        long user = requireUser(key, userId);
        if (body == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "缺少文档请求");
        return ResultUtils.success(creation.createDocument(user, body.spaceId, body.folderId,
                body.title, body.content));
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

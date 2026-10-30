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
    public BaseResponse<Map<String, Object>> readFile(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId, @RequestParam String path) {
        long user = requireUser(key, userId);
        try {
            String content = files.read(user, path);
            if (com.fasterxml.jackson.core.io.JsonStringEncoder.getInstance().quoteAsString(content).length > 8000) return ResultUtils.success(Map.of("path", path, "message", "File is large; use read_file_segment for bounded reading."));
            AgentPersonalFileStore.Segment segment = files.segment(user, path, null, 1024 * 1024);
            if (segment.next != null || !segment.text.equals(content))
                throw new IllegalStateException("Personal file version conflict");
            return ResultUtils.success(Map.of("path", path, "content", content, "referenceEvidence",
                    java.util.List.of(AgentPersonalFileEvidence.issue(secret, user, path, segment.version, 0,
                            segment.text.getBytes(StandardCharsets.UTF_8).length))));
        } catch (IllegalArgumentException e) {
            if ("File content exceeds 1 MiB".equals(e.getMessage()))
                return ResultUtils.success(Map.of("path", path, "message", "File is large; use read_file_segment for bounded reading."));
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid personal file request");
        }
    }

    @GetMapping("/files/segment")
    public BaseResponse<Map<String, Object>> readSegment(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId, @RequestParam String path,
            @RequestParam(required = false) String cursor) {
        long user = requireUser(key, userId);
        try {
            AgentPersonalFileStore.Segment result = files.segment(user, path, AgentPersonalFileStore.decodeCursor(cursor, secret), 1024);
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("text", result.text); data.put("version", result.version); data.put("start", result.start);
            data.put("referenceEvidence", java.util.List.of(AgentPersonalFileEvidence.issue(secret, user, path,
                    result.version, result.start, result.start + result.text.getBytes(StandardCharsets.UTF_8).length)));
            data.put("next", AgentPersonalFileStore.encodeCursor(result.next, secret));
            return ResultUtils.success(data);
        } catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid personal file request"); }
        catch (IllegalStateException e) { throw fileFailure(e); }
    }

    @GetMapping("/files/search")
    public BaseResponse<Map<String, Object>> searchFile(
            @RequestHeader(value = "X-Agent-Key", required = false) String key,
            @RequestParam String userId, @RequestParam String path, @RequestParam String query,
            @RequestParam(required = false) String cursor) {
        long user = requireUser(key, userId);
        try {
            AgentPersonalFileStore.SearchResult result = files.search(user, path, query, AgentPersonalFileStore.decodeCursor(cursor, secret), 4096, 20);
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("offsets", result.offsets); data.put("version", result.version);
            java.util.List<String> evidence = new java.util.ArrayList<>();
            long queryBytes = query.getBytes(StandardCharsets.UTF_8).length;
            for (long offset : result.offsets)
                evidence.add(AgentPersonalFileEvidence.issue(secret, user, path, result.version, offset, offset + queryBytes));
            data.put("referenceEvidence", evidence);
            data.put("next", AgentPersonalFileStore.encodeCursor(result.next, secret));
            return ResultUtils.success(data);
        } catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid personal file request"); }
        catch (IllegalStateException e) { throw fileFailure(e); }
    }

    private BusinessException fileFailure(IllegalStateException e) {
        if ("Personal file version conflict".equals(e.getMessage()))
            return new BusinessException(ErrorCode.PARAMS_ERROR, "Personal file version conflict");
        return new BusinessException(ErrorCode.OPERATION_ERROR, "Personal file operation failed");
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

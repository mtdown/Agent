package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.model.entity.User;
import com.et.cloud.rag.RagApiKeyService;
import com.et.cloud.rag.RagAskRequest;
import com.et.cloud.rag.RagAskService;
import com.et.cloud.rag.RagSearchRequest;
import com.et.cloud.rag.RagSearchResult;
import com.et.cloud.rag.RagSearchService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * Open, read-only RAG endpoints for external callers (e.g. a local agent)
 * authenticated by the X-API-Key header instead of a login session.
 * Both endpoints delegate to the SAME permission-filtered service layer as
 * the in-app ones, so a key can only ever reach spaces visible to its owner.
 */
@RestController
@RequestMapping("/open/rag")
public class OpenRagController {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Resource
    private RagApiKeyService ragApiKeyService;

    @Resource
    private RagSearchService ragSearchService;

    @Resource
    private RagAskService ragAskService;

    @PostMapping("/search")
    public BaseResponse<RagSearchResult> search(@RequestBody RagSearchRequest request,
                                                @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
        User owner = ragApiKeyService.resolveUser(apiKey);
        return ResultUtils.success(ragSearchService.search(owner, request));
    }

    /**
     * Streamed ask with the identical SSE protocol (meta/reason/delta/done/error)
     * as the in-app /rag/ask endpoint.
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody RagAskRequest request,
                          @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
        User owner = ragApiKeyService.resolveUser(apiKey);
        return ragAskService.ask(owner, request);
    }
}

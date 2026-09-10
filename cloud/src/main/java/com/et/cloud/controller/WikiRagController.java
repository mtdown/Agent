package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.model.entity.User;
import com.et.cloud.rag.RagAskRequest;
import com.et.cloud.rag.RagAskService;
import com.et.cloud.rag.RagSearchRequest;
import com.et.cloud.rag.RagSearchResult;
import com.et.cloud.rag.RagSearchService;
import com.et.cloud.rag.WikiChunkView;
import com.et.cloud.rag.WikiRagIndexService;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * RAG retrieval endpoints. /rag/search is the permission-filtered retrieval
 * entry reused later by the AI assistant panel; /rag/document chunks listing
 * backs the detail-page chunk preview (visible to viewers of the document).
 */
@RestController
@RequestMapping("/rag")
public class WikiRagController {

    @Resource
    private UserService userService;

    @Resource
    private RagSearchService ragSearchService;

    @Resource
    private RagAskService ragAskService;

    @Resource
    private WikiRagIndexService wikiRagIndexService;

    @Resource
    private DocumentWikiService documentWikiService;

    @PostMapping("/search")
    public BaseResponse<RagSearchResult> search(@RequestBody RagSearchRequest request,
                                                HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(ragSearchService.search(loginUser, request));
    }

    /**
     * Streamed RAG ask for the AI assistant panel (SSE: meta/reason/delta/done/error).
     * Validation throws standard BaseResponse errors before the stream starts.
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody RagAskRequest request,
                          HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ragAskService.ask(loginUser, request);
    }

    @GetMapping("/document/{docId}/chunks")
    public BaseResponse<List<WikiChunkView>> listDocumentChunks(@PathVariable("docId") Long docId,
                                                                HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(docId == null || docId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpServletRequest);
        // reuse document visibility so chunk listing can never bypass it
        documentWikiService.checkDocumentWikiVisible(loginUser, documentWikiService.getById(docId));
        return ResultUtils.success(wikiRagIndexService.listChunksOfDocument(docId));
    }
}

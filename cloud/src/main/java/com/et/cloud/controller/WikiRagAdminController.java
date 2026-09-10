package com.et.cloud.controller;

import com.et.cloud.annotation.AuthCheck;
import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.model.constant.UserConstant;
import com.et.cloud.rag.RagRebuildReport;
import com.et.cloud.rag.WikiRagIndexService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Admin-only RAG operations: full backfill (idempotent) and manual
 * reconciliation trigger (the scheduled job runs daily at 03:00 anyway).
 */
@RestController
@RequestMapping("/admin/rag")
public class WikiRagAdminController {

    @Resource
    private WikiRagIndexService wikiRagIndexService;

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/rebuild")
    public BaseResponse<RagRebuildReport> rebuild() {
        return ResultUtils.success(wikiRagIndexService.rebuildAll());
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/reconcile")
    public BaseResponse<Integer> reconcile() {
        return ResultUtils.success(wikiRagIndexService.reconcileOrphans());
    }
}

package com.et.cloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.et.cloud.dto.documentWiki.DocumentWikiQueryRequest;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.vis.DocumentWikiVis;

import javax.servlet.http.HttpServletRequest;

public interface DocumentWikiService extends IService<DocumentWiki> {

    /**
     * Build query conditions.
     */
    QueryWrapper<DocumentWiki> getQueryWrapper(DocumentWikiQueryRequest documentWikiQueryRequest);

    /**
     * Convert entity to view object and fill user info.
     */
    DocumentWikiVis getDocumentWikiVis(DocumentWiki documentWiki, HttpServletRequest request);

    /**
     * Convert page entity data to view data.
     */
    Page<DocumentWikiVis> getDocumentWikiVisPage(Page<DocumentWiki> documentWikiPage, HttpServletRequest request);

    /**
     * Validate document data.
     */
    void validDocumentWiki(DocumentWiki documentWiki);

    /**
     * Check whether the login user can edit or delete the document.
     */
    void checkDocumentWikiAuth(User loginUser, DocumentWiki documentWiki);

    /**
     * Check whether the login user can operate this document through its space.
     */
    void checkDocumentWikiVisible(User loginUser, DocumentWiki documentWiki);

    /**
     * Build list summary from content.
     */
    String buildSummary(String content);

    DocumentWiki getByIdIncludeDeleted(Long id);

    Boolean logicalDelete(Long id, Long deleteBy);

    Boolean restore(Long id);

    Boolean permanentDelete(Long id);
}

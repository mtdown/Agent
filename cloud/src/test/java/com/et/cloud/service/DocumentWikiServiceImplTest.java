package com.et.cloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.et.cloud.dto.documentWiki.DocumentWikiQueryRequest;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.vis.DocumentWikiVis;
import com.et.cloud.service.impl.DocumentWikiServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DocumentWikiServiceImplTest {

    private final DocumentWikiServiceImpl documentWikiService = new DocumentWikiServiceImpl();

    @Test
    void validDocumentWikiRejectsBlankTitle() {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setTitle(" ");
        documentWiki.setContent("valid content");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentWikiService.validDocumentWiki(documentWiki)
        );

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void validDocumentWikiRejectsBlankContent() {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setTitle("Valid title");
        documentWiki.setContent(" ");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentWikiService.validDocumentWiki(documentWiki)
        );

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void getQueryWrapperBuildsSearchAndFilterConditions() {
        DocumentWikiQueryRequest request = new DocumentWikiQueryRequest();
        request.setSearchText("redis");
        request.setMatchMode("titleOrContent");
        request.setTitle("cache");
        request.setTags(Arrays.asList("java", "wiki"));
        request.setSpaceId(3L);
        request.setVisibleSpaceIds(Arrays.asList(1L, 3L));
        request.setUserId(1L);
        request.setSortField("editTime");
        request.setSortOrder("ascend");

        QueryWrapper<DocumentWiki> queryWrapper = documentWikiService.getQueryWrapper(request);
        String sqlSegment = queryWrapper.getSqlSegment();

        assertTrue(sqlSegment.contains("title"));
        assertTrue(sqlSegment.contains("content"));
        assertTrue(sqlSegment.contains("spaceId"));
        assertTrue(sqlSegment.contains("isDelete"));
        assertTrue(sqlSegment.contains("tags"));
        assertTrue(sqlSegment.contains("userId"));
        assertTrue(sqlSegment.contains("ORDER BY editTime ASC"));
    }

    @Test
    void objToVisConvertsJsonTags() {
        DocumentWiki documentWiki = new DocumentWiki();
        documentWiki.setId(1L);
        documentWiki.setTitle("Agent Wiki");
        documentWiki.setContent("Document content");
        documentWiki.setTags("[\"java\",\"wiki\"]");

        DocumentWikiVis documentWikiVis = DocumentWikiVis.objToVis(documentWiki);

        assertEquals(documentWiki.getId(), documentWikiVis.getId());
        assertEquals(documentWiki.getTitle(), documentWikiVis.getTitle());
        assertEquals(Arrays.asList("java", "wiki"), documentWikiVis.getTags());
    }
}

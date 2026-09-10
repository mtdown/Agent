package com.et.cloud.rag;

import com.et.cloud.mapper.DocumentWikiMapper;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.service.impl.DocumentWikiServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the RAG lifecycle hooks inside DocumentWikiServiceImpl: every write
 * path (save/updateById/logicalDelete/restore/permanentDelete/moveDocument)
 * publishes the right event with the right payload.
 */
class DocumentWikiRagHookTest {

    private DocumentWikiMapper documentWikiMapper;
    private ApplicationEventPublisher eventPublisher;
    private DocumentWikiServiceImpl documentWikiService;

    @BeforeEach
    void setUp() {
        // LambdaUpdateWrapper (moveDocument) needs the MyBatis-Plus lambda cache
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                DocumentWiki.class);
        documentWikiMapper = mock(DocumentWikiMapper.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        documentWikiService = new DocumentWikiServiceImpl();
        ReflectionTestUtils.setField(documentWikiService, "baseMapper", documentWikiMapper);
        ReflectionTestUtils.setField(documentWikiService, "eventPublisher", eventPublisher);
    }

    private DocumentWiki doc(long id, long spaceId, Integer version) {
        DocumentWiki doc = new DocumentWiki();
        doc.setId(id);
        doc.setSpaceId(spaceId);
        doc.setContentVersion(version);
        doc.setContent("# 标题\n\n渝府发〔2026〕8号 正文内容");
        doc.setTitle("示例文档");
        doc.setContentFormat("markdown");
        return doc;
    }

    @Test
    void savePublishesCreatedWithVersionAndHash() {
        when(documentWikiMapper.insert(any(DocumentWiki.class))).thenReturn(1);
        DocumentWiki entity = doc(1L, 10L, null);
        assertTrue(documentWikiService.save(entity));
        ArgumentCaptor<WikiDocumentChangedEvent> captor = ArgumentCaptor.forClass(WikiDocumentChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        WikiDocumentChangedEvent event = captor.getValue();
        assertEquals(WikiDocumentChangedEvent.ChangeType.DOC_CREATED, event.getChangeType());
        assertEquals(1L, event.getDocId());
        assertEquals(10L, event.getSpaceId());
        assertEquals(1, event.getContentVersion());
        assertNotNull(entity.getContentHash(), "save must backfill content hash");
    }

    @Test
    void saveFailurePublishesNothing() {
        when(documentWikiMapper.insert(any(DocumentWiki.class))).thenReturn(0);
        documentWikiService.save(doc(1L, 10L, null));
        verify(eventPublisher, times(0)).publishEvent(any(WikiDocumentChangedEvent.class));
    }

    @Test
    void updateByIdBumpsVersionAndPublishesUpdated() {
        when(documentWikiMapper.selectById(5L)).thenReturn(doc(5L, 10L, 3));
        when(documentWikiMapper.updateById(any(DocumentWiki.class))).thenReturn(1);
        DocumentWiki edit = doc(5L, 10L, null);
        assertTrue(documentWikiService.updateById(edit));
        ArgumentCaptor<WikiDocumentChangedEvent> captor = ArgumentCaptor.forClass(WikiDocumentChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        WikiDocumentChangedEvent event = captor.getValue();
        assertEquals(WikiDocumentChangedEvent.ChangeType.DOC_UPDATED, event.getChangeType());
        assertEquals(4, event.getContentVersion());
        assertEquals(4, edit.getContentVersion());
    }

    @Test
    void logicalDeletePublishesEventWithSpaceAndVersion() {
        when(documentWikiMapper.selectByIdIncludeDeleted(5L)).thenReturn(doc(5L, 10L, 2));
        when(documentWikiMapper.logicalDeleteById(any(), any(), any())).thenReturn(1);
        assertTrue(documentWikiService.logicalDelete(5L, 99L));
        ArgumentCaptor<WikiDocumentChangedEvent> captor = ArgumentCaptor.forClass(WikiDocumentChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(WikiDocumentChangedEvent.ChangeType.DOC_LOGICAL_DELETED, captor.getValue().getChangeType());
        assertEquals(10L, captor.getValue().getSpaceId());
    }

    @Test
    void restorePublishesRestored() {
        when(documentWikiMapper.selectByIdIncludeDeleted(5L)).thenReturn(doc(5L, 10L, 2));
        when(documentWikiMapper.restoreById(5L)).thenReturn(1);
        assertTrue(documentWikiService.restore(5L));
        ArgumentCaptor<WikiDocumentChangedEvent> captor = ArgumentCaptor.forClass(WikiDocumentChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(WikiDocumentChangedEvent.ChangeType.DOC_RESTORED, captor.getValue().getChangeType());
        assertEquals(2, captor.getValue().getContentVersion());
    }

    @Test
    void permanentDeletePublishesEvent() {
        when(documentWikiMapper.selectByIdIncludeDeleted(5L)).thenReturn(doc(5L, 10L, 2));
        when(documentWikiMapper.physicallyDeleteById(5L)).thenReturn(1);
        assertTrue(documentWikiService.permanentDelete(5L));
        ArgumentCaptor<WikiDocumentChangedEvent> captor = ArgumentCaptor.forClass(WikiDocumentChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(WikiDocumentChangedEvent.ChangeType.DOC_PERMANENT_DELETED, captor.getValue().getChangeType());
    }

    @Test
    void moveDocumentPublishesMovedWithFromAndTo() {
        when(documentWikiMapper.selectById(5L)).thenReturn(doc(5L, 10L, 2));
        when(documentWikiMapper.update(any(), any())).thenReturn(1);
        assertTrue(documentWikiService.moveDocument(5L, 20L, null));
        ArgumentCaptor<WikiDocumentChangedEvent> captor = ArgumentCaptor.forClass(WikiDocumentChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        WikiDocumentChangedEvent event = captor.getValue();
        assertEquals(WikiDocumentChangedEvent.ChangeType.DOC_MOVED, event.getChangeType());
        assertEquals(10L, event.getSpaceId());
        assertEquals(20L, event.getTargetSpaceId());
    }
}

package com.et.cloud.service.impl;

import com.et.cloud.model.dto.ImportedWikiDocument;
import com.et.cloud.model.entity.*;
import com.et.cloud.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentUrlImportTest {
    @Test void commitFailureStopsRemainingItemsAndNeverRetriesSave() throws Exception {
        Fixture f=new Fixture();
        doThrow(new RuntimeException("commit uncertain")).when(f.manager).commit(any());
        // First preflight is a transaction too; make its commit succeed.
        doNothing().doThrow(new RuntimeException("commit uncertain")).when(f.manager).commit(any());
        Map<String,Object> out=f.service.importUrls(7,11,null,List.of("https://example.org/a","https://example.org/b"));
        assertEquals("unknown",out.get("status"));
        List<?> items=(List<?>)out.get("items");
        assertEquals("unknown",((Map<?,?>)items.get(0)).get("status"));
        assertEquals("not_executed",((Map<?,?>)items.get(1)).get("status"));
        verify(f.docs,times(1)).save(any()); verifyNoInteractions(f.cache);
    }
    @Test void cacheFailureOnlyRetriesCacheAndPreservesConfirmedDocument() throws Exception {
        Fixture f=new Fixture();
        doThrow(new IllegalStateException("sensitive")).when(f.cache).clearSpace(11L);
        Map<?,?> item=(Map<?,?>)((List<?>)f.service.importUrls(7,11,null,List.of("https://example.org/a")).get("items")).get(0);
        assertEquals("success",item.get("status")); assertEquals("31",item.get("documentId"));
        assertEquals("文档已保存，缓存刷新失败",item.get("warning"));
        verify(f.docs,times(1)).save(any()); verify(f.cache,times(2)).clearSpace(11L);
    }
    @Test void cacheRetrySuccessDoesNotSaveTwice() throws Exception {
        Fixture f=new Fixture();
        doThrow(new IllegalStateException()).doNothing().when(f.cache).clearSpace(11L);
        Map<?,?> item=(Map<?,?>)((List<?>)f.service.importUrls(7,11,null,List.of("https://example.org/a")).get("items")).get(0);
        assertEquals("success",item.get("status")); assertFalse(item.containsKey("warning"));
        verify(f.docs,times(1)).save(any()); verify(f.cache,times(2)).clearSpace(11L);
    }
    @Test void lostPermissionBeforeWritePreventsSave() throws Exception {
        Fixture f=new Fixture();
        when(f.spaces.requireEditableSpace(eq(11L),any())).thenReturn(f.space).thenThrow(new IllegalStateException());
        Map<?,?> item=(Map<?,?>)((List<?>)f.service.importUrls(7,11,null,List.of("https://example.org/a")).get("items")).get(0);
        assertEquals("failed",item.get("status")); verify(f.docs,never()).save(any());
    }
    @Test void privateAddressIsRejectedBySharedFetcherBeforeNetwork() {
        assertThrows(com.et.cloud.exception.BusinessException.class, () -> new HttpWebPageFetcher().fetch("http://127.0.0.1/admin"));
    }
    @Test void internalEndpointRequiresKeyAndBindsUserContext() {
        var controller=new com.et.cloud.controller.AgentResourceInternalController(null,"secret");
        AgentUrlImportService service=mock(AgentUrlImportService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"urlImports",service);
        var body=new com.et.cloud.controller.AgentResourceInternalController.WikiUrlImportRequest();
        body.spaceId="11"; body.folderId=null; body.urls=List.of("https://example.org/a");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.importWikiUrls(null,"7",body));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.importWikiUrls("wrong","7",body));
        verifyNoInteractions(service);
        var result=controller.importWikiUrls("secret","7",body).getData();
        assertEquals(true,result.get("importConfirmationRequired"));
        assertEquals("11",result.get("spaceId")); assertEquals(body.urls,result.get("urls"));
        verifyNoInteractions(service);
    }
    @Test void mixedProjectionPreservesEveryResultAndKeepsIdsInternal() throws Exception {
        var choices=mock(com.et.cloud.controller.AgentWikiChoiceService.class);
        User owner=new User(); owner.setId(7L);
        var projection=new com.et.cloud.controller.AgentWikiChoiceProjection(choices,owner,1L,2L);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        projection.project("wiki_tool_event",json.readTree("{\"name\":\"import_wiki_urls\",\"result\":{\"items\":["+
                "{\"input\":\"https://example.org/a\",\"sourceUrl\":\"https://example.org/final\",\"status\":\"success\",\"spaceId\":\"11\",\"documentId\":\"999999999\",\"title\":\"Page\",\"warning\":\"cache\"},"+
                "{\"input\":\"https://example.org/b\",\"status\":\"unknown\"},"+
                "{\"input\":\"https://example.org/c\",\"status\":\"not_executed\"}]}}"));
        var state=json.readTree(projection.interactionJson());
        assertEquals(3,state.path("operations").size()); assertEquals("unknown",state.path("operations").get(1).path("status").asText());
        var impl=new com.et.cloud.controller.AgentWikiChoiceService(mock(javax.sql.DataSource.class),null,null,null);
        var method=impl.getClass().getDeclaredMethod("assembleDisplay",com.fasterxml.jackson.databind.JsonNode.class,List.class); method.setAccessible(true);
        Map<String,Object> doc=new LinkedHashMap<>(); doc.put("kind","wiki_write_result"); doc.put("documentTitle","Page"); doc.put("spaceLabel","Space"); doc.put("folderName","");
        @SuppressWarnings("unchecked") Map<String,Object> display=(Map<String,Object>)method.invoke(impl,state,List.of(doc));
        String text=com.et.cloud.controller.AgentWikiChoiceService.answerFor(display,1,0);
        assertTrue(text.contains("https://example.org/final")); assertTrue(text.contains("缓存刷新失败"));
        assertTrue(text.contains("结果未知")); assertTrue(text.contains("未执行")); assertFalse(text.contains("999999999"));
    }
    @Test void explicitImportRejectionRemainsFailed() throws Exception {
        var choices=mock(com.et.cloud.controller.AgentWikiChoiceService.class);
        User owner=new User(); owner.setId(7L);
        var projection=new com.et.cloud.controller.AgentWikiChoiceProjection(choices,owner,1L,2L);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        projection.project("wiki_tool_event",json.readTree("{\"name\":\"import_wiki_urls\",\"result\":{\"saved\":false,\"code\":40300}}"));
        assertEquals("failed",json.readTree(projection.interactionJson()).path("operations").get(0).path("status").asText());
    }
    @Test void crossSpaceOrDeletedFolderNeverStartsFetchOrSave() throws Exception {
        Fixture f=new Fixture();
        when(f.folders.requireFolderAccess(99L,11L,f.user,true)).thenThrow(new IllegalStateException("invalid folder"));
        Map<?,?> item=(Map<?,?>)((List<?>)f.service.importUrls(7,11,99L,List.of("https://example.org/a")).get("items")).get(0);
        assertEquals("failed",item.get("status")); verify(f.core,never()).prepareUrl(anyString()); verify(f.docs,never()).save(any());
    }
    @Test void validFolderIsPassedToTheSharedDocumentBuilder() throws Exception {
        Fixture f=new Fixture(); WikiFolder folder=new WikiFolder(); folder.setId(22L); folder.setName("folder");
        when(f.folders.requireFolderAccess(22L,11L,f.user,true)).thenReturn(folder);
        Map<?,?> item=(Map<?,?>)((List<?>)f.service.importUrls(7,11,22L,List.of("https://example.org/a")).get("items")).get(0);
        assertEquals("success",item.get("status")); assertEquals("22",item.get("folderId"));
        verify(f.core).buildDocument(any(),eq(f.space),eq(22L),eq(f.user));
    }
    @Test void failedFetchDoesNotLoseOtherSuccessfulItems() throws Exception {
        Fixture f=new Fixture();
        when(f.core.prepareUrl("https://example.org/b")).thenThrow(new java.io.IOException("failed"));
        List<?> items=(List<?>)f.service.importUrls(7,11,null,List.of("https://example.org/a","https://example.org/b","https://example.org/c")).get("items");
        assertEquals("success",((Map<?,?>)items.get(0)).get("status"));
        assertEquals("failed",((Map<?,?>)items.get(1)).get("status"));
        assertEquals("success",((Map<?,?>)items.get(2)).get("status"));
        verify(f.docs,times(2)).save(any());
    }
    static class Fixture {
        WikiBatchImportServiceImpl core=mock(WikiBatchImportServiceImpl.class);
        WikiMutationLock lock=mock(WikiMutationLock.class);
        WikiSpaceService spaces=mock(WikiSpaceService.class);
        WikiFolderService folders=mock(WikiFolderService.class);
        DocumentWikiService docs=mock(DocumentWikiService.class);
        WikiCacheManager cache=mock(WikiCacheManager.class);
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        WikiSpace space=new WikiSpace(); User user=new User();
        AgentUrlImportService service;
        Fixture() throws Exception {
            space.setId(11L); user.setId(7L);
            when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
            when(lock.acquire(11,7)).thenReturn(user);
            when(spaces.requireEditableSpace(11L,user)).thenReturn(space);
            ImportedWikiDocument imported=new ImportedWikiDocument(); imported.setTitle("title"); imported.setSourceUrl("https://example.org/a");
            when(core.prepareUrl(anyString())).thenReturn(imported);
            when(core.buildDocument(any(),any(),any(),any())).thenAnswer(inv -> new DocumentWiki());
            when(docs.save(any())).thenAnswer(inv -> { ((DocumentWiki)inv.getArgument(0)).setId(31L); return true; });
            service=new AgentUrlImportService(core,lock,spaces,folders,docs,cache,manager);
        }
    }

    @Test void internalImportRequestDoesNotWriteWithoutUserConfirmation() {
        var service=mock(AgentUrlImportService.class);
        var controller=new com.et.cloud.controller.AgentResourceInternalController(null,"secret");
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"urlImports",service);
        var body=new com.et.cloud.controller.AgentResourceInternalController.WikiUrlImportRequest();
        body.spaceId="11"; body.urls=List.of("https://example.org/a");
        controller.importWikiUrls("secret","7",body);
        verifyNoInteractions(service);
    }

    @Test void changedConfirmedFolderPathRejectsAtWriteBoundary() throws Exception {
        Fixture f=new Fixture(); f.space.setType(WikiSpaceService.TYPE_PUBLIC); f.space.setName("公开文档");
        WikiFolder folder=new WikiFolder(); folder.setId(22L); folder.setName("renamed");
        when(f.folders.requireFolderAccess(22L,11L,f.user,true)).thenReturn(folder);
        var snapshot=new AgentUrlImportService.TargetSnapshot("公开空间",List.of("22:original"));
        Map<?,?> item=(Map<?,?>)((List<?>)f.service.importUrls(7,11,22L,List.of("https://example.org/a"),snapshot).get("items")).get(0);
        assertEquals("failed",item.get("status")); verify(f.docs,never()).save(any());
    }
    @Test void changedConfirmedSpaceNameRejectsAtWriteBoundary() throws Exception {
        Fixture f=new Fixture(); f.space.setType(WikiSpaceService.TYPE_TEAM); f.space.setName("renamed");
        var snapshot=new AgentUrlImportService.TargetSnapshot("团队「original」",List.of());
        Map<?,?> item=(Map<?,?>)((List<?>)f.service.importUrls(7,11,null,List.of("https://example.org/a"),snapshot).get("items")).get(0);
        assertEquals("failed",item.get("status")); verify(f.docs,never()).save(any());
    }
}

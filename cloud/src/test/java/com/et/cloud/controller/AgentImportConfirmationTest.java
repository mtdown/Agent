package com.et.cloud.controller;

import com.et.cloud.model.entity.User;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.WikiSpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentImportConfirmationTest {
    static final String CONFIRM="a".repeat(43), CANCEL="b".repeat(43);
    static class Fixture {
        final JdbcTemplate jdbc=mock(JdbcTemplate.class);
        final AgentWikiChoiceService choices=new AgentWikiChoiceService(mock(DataSource.class),null,mock(WikiSpaceService.class),mock(DocumentWikiService.class));
        final User owner=new User();
        Fixture(String status,long expiresAt) {
            owner.setId(7L); ReflectionTestUtils.setField(choices,"jdbc",jdbc);
            String interaction="{\"kind\":\"import_confirmation\",\"ownerId\":\"7\",\"conversationId\":\"8\",\"turnId\":\"9\",\"spaceId\":\"11\",\"folderId\":\"22\",\"spaceLabel\":\"团队\",\"urls\":[\"https://example.org/a\"],\"confirmToken\":\""+CONFIRM+"\",\"cancelToken\":\""+CANCEL+"\",\"expiresAt\":"+expiresAt+",\"status\":\""+status+"\"}";
            when(jdbc.query(startsWith("SELECT busy"),any(RowMapper.class),anyLong(),anyLong())).thenReturn(List.of(0));
            when(jdbc.queryForObject(startsWith("SELECT MAX"),eq(Long.class),anyLong())).thenReturn(9L);
            when(jdbc.queryForList(startsWith("SELECT id"),anyLong(),anyLong())).thenReturn(List.of(Map.of("id",10L,"interactionJson",interaction,"choiceConsumed",0)));
            when(jdbc.update(startsWith("UPDATE agent_message"),anyString(),any())).thenReturn(1,0);
        }
    }
    @Test void confirmReadsBoundParametersAndCannotBeConsumedTwice() {
        Fixture f=new Fixture("pending",System.currentTimeMillis()+60000);
        var selected=f.choices.claim(f.owner,8,9,CONFIRM);
        assertEquals("11",selected.spaceId); assertEquals("22",selected.folderId);
        assertEquals(List.of("https://example.org/a"),selected.urls); assertFalse(selected.cancelled);
        assertThrows(IllegalArgumentException.class,()->f.choices.claim(f.owner,8,9,CONFIRM));
    }
    @Test void cancelIsConsumedAndDoesNotAuthorizeExecution() {
        Fixture f=new Fixture("pending",System.currentTimeMillis()+60000);
        assertTrue(f.choices.claim(f.owner,8,9,CANCEL).cancelled);
        assertThrows(IllegalArgumentException.class,()->f.choices.claim(f.owner,8,9,CONFIRM));
    }
    @Test void forgedExpiredCrossUserAndCrossConversationAreRejected() {
        Fixture f=new Fixture("pending",System.currentTimeMillis()+60000);
        assertThrows(IllegalArgumentException.class,()->f.choices.claim(f.owner,8,9,"x".repeat(43)));
        User other=new User(); other.setId(12L);
        assertThrows(IllegalArgumentException.class,()->f.choices.claim(other,8,9,CONFIRM));
        assertThrows(IllegalArgumentException.class,()->f.choices.claim(f.owner,99,9,CONFIRM));
        Fixture expired=new Fixture("pending",System.currentTimeMillis()-1);
        assertThrows(IllegalArgumentException.class,()->expired.choices.claim(expired.owner,8,9,CONFIRM));
        verify(f.jdbc,never()).update(startsWith("UPDATE agent_message"),anyString(),any());
    }

    @Test void resultPersistenceFailureDoesNotClaimSuccessOrRetry() throws Exception {
        var choices=mock(AgentWikiChoiceService.class);
        var users=mock(com.et.cloud.service.UserService.class);
        var request=new org.springframework.mock.web.MockHttpServletRequest(); User owner=new User(); owner.setId(7L);
        when(users.getLoginUser(request)).thenReturn(owner);
        var selected=new AgentWikiChoiceService.Selection(null,"11","公开空间","import_confirmation",null,"10",List.of("https://example.org/a"),List.of(),false);
        when(choices.claim(owner,8,9,CONFIRM,false)).thenReturn(selected);
        var imports=mock(com.et.cloud.service.impl.AgentUrlImportService.class);
        when(imports.importUrls(eq(7L),eq(11L),isNull(),anyList(),any())).thenReturn(Map.of("items",List.of(Map.of("status","success","documentId","31","spaceId","11","title","Page"))));
        doThrow(new IllegalStateException("mock persistence failure")).when(choices).finishImport(eq(owner),eq(8L),eq(selected),anyString());
        var controller=new AgentPreviewController(users,mock(AgentPreviewProxy.class),mock(AgentConversationStore.class),mock(com.et.cloud.rag.RagSearchService.class),mock(WikiSpaceService.class),new AgentSkillCatalog(mock(AgentLibraryStore.class)),choices);
        ReflectionTestUtils.setField(controller,"urlImports",imports);
        var choice=new AgentPreviewController.WikiChoice(); choice.turnId="9"; choice.token=CONFIRM;
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        controller.selectWikiChoice("8",choice,request).writeTo(out);
        String text=out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("结果未知")); assertFalse(text.contains("已创建文档"));
        verify(imports,times(1)).importUrls(eq(7L),eq(11L),isNull(),anyList(),any());
    }
    @Test void changedTargetResultPromptsUserToConfirmAgain() throws Exception {
        var choices=mock(AgentWikiChoiceService.class); User owner=new User(); owner.setId(7L);
        var projection=new AgentWikiChoiceProjection(choices,owner,8,9);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        projection.project("wiki_tool_event",json.readTree("{\"name\":\"import_wiki_urls\",\"result\":{\"items\":[{\"input\":\"https://example.org/a\",\"status\":\"failed\",\"message\":\"目标名称或目录路径已变化，请重新确认\"}]}}"));
        var real=new AgentWikiChoiceService(mock(DataSource.class),null,null,null);
        var method=real.getClass().getDeclaredMethod("assembleDisplay",com.fasterxml.jackson.databind.JsonNode.class,List.class); method.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,Object> display=(Map<String,Object>)method.invoke(real,json.readTree(projection.interactionJson()),List.of());
        assertTrue(AgentWikiChoiceService.answerFor(display,0,1).contains("重新确认"));
    }

    @Test void confirmationBodyRejectsAdditionalTargetOrUrls() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var input=json.readValue("{\"turnId\":\"9\",\"token\":\"x\",\"spaceId\":\"77\",\"urls\":[\"https://example.org/other\"]}",AgentPreviewController.WikiChoice.class);
        assertTrue(input.unexpectedFields);
        Fixture f=new Fixture("pending",System.currentTimeMillis()+60000);
        assertThrows(IllegalArgumentException.class,()->f.choices.claim(f.owner,8,9,CONFIRM,input.unexpectedFields));
        verify(f.jdbc,never()).update(startsWith("UPDATE agent_message"),anyString(),any());
    }
}

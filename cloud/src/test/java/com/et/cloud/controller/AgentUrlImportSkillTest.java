package com.et.cloud.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentUrlImportSkillTest {
    @Test void actualProjectionKeepsEachImportResultVisibleWithoutInternalIds() throws Exception {
        var user = new com.et.cloud.model.entity.User(); user.setId(7L);
        var choices = spy(new AgentWikiChoiceService(mock(DataSource.class), null,
                mock(com.et.cloud.service.WikiSpaceService.class), mock(com.et.cloud.service.DocumentWikiService.class)));
        doReturn("团队").when(choices).displayLabelFor(user,"11");
        doReturn(Map.of("77",Map.of("spaceLabel","团队","folderName","目标目录","documentTitle","文章A","validPath","true")))
                .when(choices).resolveDocuments(eq(user),anyCollection());
        var projection = new AgentWikiChoiceProjection(choices,user,8L,9L);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var event = json.readTree("{\"name\":\"import_wiki_urls\",\"result\":{\"items\":["
                + "{\"input\":\"https://example.org/a\",\"sourceUrl\":\"https://example.org/a\",\"status\":\"success\",\"documentId\":\"77\",\"spaceId\":\"11\",\"folderId\":\"22\",\"title\":\"文章A\"},"
                + "{\"input\":\"https://example.org/b\",\"status\":\"failed\"},"
                + "{\"input\":\"https://example.org/c\",\"status\":\"unknown\"},"
                + "{\"input\":\"https://example.org/d\",\"status\":\"not_executed\"}]}}");
        projection.project("wiki_tool_event",event);
        Map<String,Object> display = projection.displayInteraction(projection.interactionJson());
        List<?> results = (List<?>)display.get("results");
        assertEquals(4,results.size());
        assertEquals("wiki_write_result",((Map<?,?>)results.get(0)).get("kind"));
        assertEquals("failed",((Map<?,?>)results.get(1)).get("status"));
        assertEquals("unknown",((Map<?,?>)results.get(2)).get("status"));
        assertEquals("not_executed",((Map<?,?>)results.get(3)).get("status"));
        String answer = projection.displayAnswer();
        for (String suffix : List.of("/a","/b","/c","/d")) assertTrue(answer.contains("https://example.org"+suffix));
        assertTrue(answer.contains("文章A")); assertTrue(answer.contains("结果未知")); assertTrue(answer.contains("未执行"));
        assertFalse(json.writeValueAsString(display).contains("documentId"));
        assertFalse(answer.contains("77"));
    }
    @Test void builtInIsAvailableAndReadAtFixedVersion() {
        AgentLibraryStore store = new AgentLibraryStore(mock(DataSource.class), mock(PlatformTransactionManager.class),
                new AgentSkillDefinitionValidator(), new AgentCodeVersionValidator());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(store, "jdbc", jdbc);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenAnswer(inv -> new ArrayList<>());
        assertTrue(store.listAvailable(7).stream().anyMatch(s -> "builtin:wiki-url-import".equals(s.id)));
        assertTrue(new AgentSkillCatalog(store).forUser(7).contains("builtin:wiki-url-import@1"));
        var version = store.enabledSkillVersion(7, "builtin:wiki-url-import", 1);
        JsonNode definition = new AgentSkillDefinitionValidator().validate(version.definitionJson, (a,b,c) -> false);
        String body = definition.toString();
        assertTrue(body.contains("import_wiki_urls"));
        assertTrue(body.contains("list_writable_wiki_locations"));
        assertTrue(body.contains("plan_wiki_destination"));
        assertTrue(body.contains("unknown"));
        assertTrue(body.contains("摘要"));
        assertThrows(IllegalArgumentException.class, () -> store.enabledSkillVersion(7, "builtin:wiki-url-import", 2));
    }

    @Test void importDraftDisplaysExactLinksAndHidesIdentifiers() throws Exception {
        var users=mock(com.et.cloud.service.UserService.class);
        var spaces=mock(com.et.cloud.service.WikiSpaceService.class);
        var folders=mock(com.et.cloud.mapper.WikiFolderMapper.class);
        var owner=new com.et.cloud.model.entity.User(); owner.setId(7L);
        var space=new com.et.cloud.model.entity.WikiSpace(); space.setId(11L); space.setType(com.et.cloud.service.WikiSpaceService.TYPE_PUBLIC); space.setName("Public");
        when(users.getById(7L)).thenReturn(owner);
        when(spaces.listVisibleSpaceIds(owner)).thenReturn(List.of(11L));
        when(spaces.listByIds(anyCollection())).thenReturn(List.of(space));
        when(spaces.checkSpaceAccess(space,owner,true)).thenReturn(true);
        var choices=new AgentWikiChoiceService(mock(DataSource.class),new AgentWritableWikiLocations(users,spaces,folders),spaces,mock(com.et.cloud.service.DocumentWikiService.class));
        var request=new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"importConfirmationRequired\":true,\"spaceId\":\"11\",\"folderId\":null,\"urls\":[\"https://example.org/a\",\"https://example.org/b\"]}");
        var prepared=choices.prepare(owner,8L,9L,request);
        assertEquals("import_confirmation",prepared.display.get("kind"));
        assertEquals("根目录",prepared.display.get("folderName"));
        assertEquals(List.of("https://example.org/a","https://example.org/b"),prepared.display.get("urls"));
        assertNotEquals(prepared.display.get("confirmToken"),prepared.display.get("cancelToken"));
        assertFalse(prepared.display.containsKey("spaceId"));
        assertEquals("pending",prepared.display.get("status"));
        var restored=AgentWikiChoiceService.safeDisplay(prepared.internalJson,true);
        assertFalse(restored.containsKey("confirmToken"));
    }
}

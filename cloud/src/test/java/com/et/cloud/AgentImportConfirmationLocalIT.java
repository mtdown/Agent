package com.et.cloud;

import com.et.cloud.controller.AgentPreviewController;
import com.et.cloud.controller.AgentWikiChoiceService;
import com.et.cloud.model.dto.FetchedPage;
import com.et.cloud.model.entity.User;
import com.et.cloud.rag.WikiRagIndexService;
import com.et.cloud.service.WebPageFetcher;
import com.et.cloud.service.WikiCacheManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@Import(CloudApplicationTests.GuardedDataSourceConfig.class)
class AgentImportConfirmationLocalIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) throws Exception {
        LocalFullTestSchema.mysql(properties); properties.add("spring.session.store-type",()->"none");
    }
    @AfterAll static void cleanup() throws Exception { LocalFullTestSchema.cleanup(); }
    @MockBean WikiCacheManager cache;
    @MockBean WikiRagIndexService rag;
    @MockBean WebPageFetcher fetcher;
    @Autowired JdbcTemplate jdbc;
    @Autowired AgentWikiChoiceService choices;
    @Autowired AgentPreviewController controller;
    final ObjectMapper json=new ObjectMapper();

    @Test void concurrentConfirmImportsOnlyOnceAndCancelDoesNotImport() throws Exception {
        long userId=Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM user WHERE userRole='admin' LIMIT 1",Long.class));
        long spaceId=Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM wiki_space WHERE type=2 AND isDelete=0 LIMIT 1",Long.class));
        User user=new User(); user.setId(userId);
        FetchedPage page=new FetchedPage(); page.setFinalUrl("https://example.org/a"); page.setPageTitle("Example");
        page.setHtml("<html><body><article><p>"+"example content ".repeat(80)+"</p></article></body></html>");
        when(fetcher.fetch(anyString())).thenReturn(page);
        var draft=choices.prepare(user,8001,9001,json.readTree("{\"importConfirmationRequired\":true,\"spaceId\":\""+spaceId+"\",\"folderId\":null,\"urls\":[\"https://example.org/a\"]}"));
        jdbc.update("INSERT INTO agent_conversation(id,ownerUserId,title,busy,isDelete) VALUES(8001,?,'isolated',0,0)",userId);
        jdbc.update("INSERT INTO agent_message(id,conversationId,turnId,role,content,status) VALUES(9001,8001,9001,'user','test','DONE')");
        jdbc.update("INSERT INTO agent_message(id,conversationId,turnId,role,content,status,interactionJson,choiceConsumed) VALUES(9002,8001,9001,'assistant','test','DONE',?,0)",draft.internalJson);
        String token=(String)draft.display.get("confirmToken");
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(int i=0;i<2;i++) futures.add(pool.submit(()->{
                MockHttpServletRequest request=new MockHttpServletRequest(); request.getSession().setAttribute("user_login",user);
                AgentPreviewController.WikiChoice choice=new AgentPreviewController.WikiChoice(); choice.turnId="9001"; choice.token=token;
                ready.countDown(); go.await();
                try {controller.selectWikiChoice("8001",choice,request).writeTo(new ByteArrayOutputStream()); return true;}
                catch (IllegalArgumentException rejected) {return false;}
            }));
            assertTrue(ready.await(10,TimeUnit.SECONDS)); go.countDown();
            int accepted=0; for(Future<Boolean> future:futures) if(future.get(30,TimeUnit.SECONDS)) accepted++;
            assertEquals(1,accepted);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE title='Example'",Integer.class));
            verify(fetcher,times(1)).fetch("https://example.org/a");
            String stored=jdbc.queryForObject("SELECT interactionJson FROM agent_message WHERE id=9002",String.class);
            assertEquals("wiki_write_result",json.readTree(stored).path("kind").asText());
        } finally {pool.shutdownNow();}
        var cancelled=choices.prepare(user,8002,9011,json.readTree("{\"importConfirmationRequired\":true,\"spaceId\":\""+spaceId+"\",\"folderId\":null,\"urls\":[\"https://example.org/a\"]}"));
        jdbc.update("INSERT INTO agent_conversation(id,ownerUserId,title,busy,isDelete) VALUES(8002,?,'isolated',0,0)",userId);
        jdbc.update("INSERT INTO agent_message(id,conversationId,turnId,role,content,status) VALUES(9011,8002,9011,'user','test','DONE')");
        jdbc.update("INSERT INTO agent_message(id,conversationId,turnId,role,content,status,interactionJson,choiceConsumed) VALUES(9012,8002,9011,'assistant','test','DONE',?,0)",cancelled.internalJson);
        MockHttpServletRequest request=new MockHttpServletRequest(); request.getSession().setAttribute("user_login",user);
        AgentPreviewController.WikiChoice choice=new AgentPreviewController.WikiChoice(); choice.turnId="9011"; choice.token=(String)cancelled.display.get("cancelToken");
        controller.selectWikiChoice("8002",choice,request).writeTo(new ByteArrayOutputStream());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE title='Example'",Integer.class));
        String state=jdbc.queryForObject("SELECT interactionJson FROM agent_message WHERE id=9012",String.class);
        assertEquals("cancelled",json.readTree(state).path("status").asText());
    }
}

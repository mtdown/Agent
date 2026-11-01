package com.et.cloud.service.impl;

import com.et.cloud.model.dto.FetchedPage;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.rag.WikiRagIndexService;
import com.et.cloud.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.regex.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
class AgentUrlImportConcurrencyLocalIT {
    private static String schemaName, schemaToken, serverUuid, jdbcUrl, username, password;
    private static int mysqlPort, serverPort;
    @Autowired JdbcTemplate jdbc;
    @Autowired AgentUrlImportService imports;
    @Autowired WikiSpaceService spaces;
    @Autowired WikiFolderService folders;
    @Autowired PlatformTransactionManager manager;
    @MockBean WebPageFetcher fetcher;
    @MockBean WikiCacheManager cache;
    @MockBean WikiRagIndexService rag;
    @SpyBean DocumentWikiService documents;
    long userId, adminId, spaceId, parentId, folderId;
    User user, admin;

    @DynamicPropertySource static void mysql(DynamicPropertyRegistry properties) throws Exception {
        username = requiredEnv("WIKI_IT_MYSQL_USER");
        password = requiredEnv("WIKI_IT_MYSQL_PASSWORD");
        String port = requiredEnv("WIKI_IT_MYSQL_PORT");
        if (!port.matches("[0-9]{1,5}")) throw new IllegalStateException("Invalid local test MySQL port");
        mysqlPort = Integer.parseInt(port);
        if (mysqlPort < 1 || mysqlPort > 65535) throw new IllegalStateException("Invalid local test MySQL port");
        schemaToken = UUID.randomUUID().toString().replace("-", "");
        schemaName = "codex_wiki_it_" + schemaToken;
        String serverUrl = "jdbc:mysql://127.0.0.1:" + mysqlPort + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        jdbcUrl = "jdbc:mysql://127.0.0.1:" + mysqlPort + "/" + schemaName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection server = DriverManager.getConnection(serverUrl, username, password);
             Statement sql = server.createStatement()) {
            assertServer(server);
            try (ResultSet rs = sql.executeQuery("SELECT @@server_uuid")) { if (!rs.next()) throw new IllegalStateException("Missing server identity"); serverUuid = rs.getString(1); }
            // No IF NOT EXISTS: a collision or pre-existing schema must never be reused.
            sql.execute("CREATE DATABASE " + schemaName + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            System.out.println("Local test schema created: " + schemaName);
        }
        try (Connection isolated = DriverManager.getConnection(jdbcUrl, username, password);
             Statement sql = isolated.createStatement()) {
            assertSchema(isolated, false);
            try (ResultSet rs = sql.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {
                if (!rs.next() || rs.getLong(1) != 0) throw new IllegalStateException("New test schema is not empty");
            }
            sql.execute("CREATE TABLE codex_test_owner (token VARCHAR(32) NOT NULL)");
            try (PreparedStatement marker = isolated.prepareStatement("INSERT INTO codex_test_owner(token) VALUES(?)")) { marker.setString(1, schemaToken); marker.executeUpdate(); }
            assertSchema(isolated, true);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { cleanup(); } catch (Exception failure) { System.err.println("Test schema cleanup not confirmed: " + failure.getClass().getSimpleName()); }
            }));
            Path schema = Path.of("deploy/schema-init.sql"); if (!Files.exists(schema)) schema = Path.of("../deploy/schema-init.sql");
            String source = Files.readString(schema, StandardCharsets.UTF_8);
            Matcher creates = Pattern.compile("(?is)CREATE\\s+TABLE\\s+\\x60([a-z_]+)\\x60\\s*\\(.*?\\)\\s*ENGINE\\s*=.*?;").matcher(source);
            Set<String> expected = Set.of("document_wiki","picture","rag_api_key","space","space_user","user","wiki_attachment","wiki_chunk","wiki_folder","wiki_space","wiki_space_user");
            Set<String> found = new HashSet<>();
            List<String> definitions = new ArrayList<>();
            while (creates.find()) {
                String name = creates.group(1);
                if (!expected.contains(name) || !found.add(name)) throw new IllegalStateException("Unexpected test schema definition");
                definitions.add(creates.group());
            }
            if (!found.equals(expected)) throw new IllegalStateException("Incomplete test schema definitions");
            for (String definition : definitions) { assertSchema(isolated, true); sql.execute(definition); }
        }
        Path files = Files.createTempDirectory("codex-wiki-it-files-");
        properties.add("spring.datasource.url", () -> jdbcUrl);
        properties.add("spring.datasource.username", () -> username);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.redis.host", () -> "127.0.0.1");
        properties.add("spring.redis.port", () -> "1");
        properties.add("cos.client.secretId", () -> "test-placeholder");
        properties.add("cos.client.secretKey", () -> "test-placeholder");
        properties.add("aliYunAi.apiKey", () -> "test-placeholder");
        properties.add("agent.workspace-root", () -> files.resolve("workspace").toString());
        properties.add("agent.upload-temp-root", () -> files.resolve("uploads").toString());
    }
    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing isolated test setting: " + name);
        return value;
    }
    private static void assertServer(Connection connection) throws SQLException {
        String url = connection.getMetaData().getURL();
        if (url == null || !url.startsWith("jdbc:mysql://127.0.0.1:" + mysqlPort + "/")) throw new IllegalStateException("Unexpected JDBC endpoint");
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT @@port")) {
            if (!rs.next() || rs.getInt(1) < 1) throw new IllegalStateException("Invalid MySQL server port");
            int actual = rs.getInt(1);
            if (serverPort == 0) serverPort = actual;
            else if (serverPort != actual) throw new IllegalStateException("MySQL server port changed");
        }
    }
    private static void assertSchema(Connection connection, boolean markerRequired) throws SQLException {
        if (schemaName == null || !schemaName.matches("codex_wiki_it_[0-9a-f]{32}")) throw new IllegalStateException("Invalid test schema identity");
        assertServer(connection);
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT DATABASE(), @@server_uuid")) {
            if (!rs.next() || !schemaName.equals(rs.getString(1)) || !serverUuid.equals(rs.getString(2))) throw new IllegalStateException("Unexpected test schema or server identity");
        }
        if (markerRequired) try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM codex_test_owner WHERE token=?")) {
            statement.setString(1, schemaToken);
            try (ResultSet rs = statement.executeQuery()) { if (!rs.next() || rs.getInt(1) != 1) throw new IllegalStateException("Test schema ownership marker missing"); }
        }
    }
    @AfterAll static synchronized void cleanup() throws Exception {
        if (schemaName == null || serverUuid == null) return;
        try (Connection isolated = DriverManager.getConnection(jdbcUrl, username, password);
             Statement sql = isolated.createStatement()) {
            assertSchema(isolated, true);
            sql.execute("DROP DATABASE " + schemaName);
            try (PreparedStatement check = isolated.prepareStatement("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name=?")) {
                check.setString(1, schemaName);
                try (ResultSet rs = check.executeQuery()) { if (!rs.next() || rs.getInt(1) != 0) throw new IllegalStateException("Test schema cleanup not confirmed"); }
            }
            System.out.println("Local test schema removed: " + schemaName);
            schemaName = null;
        }
    }
    @BeforeEach void setup() throws Exception {
        try (Connection connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) { assertSchema(connection, true); }
        reset(fetcher,cache,documents);
        String suffix=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","it-user-"+suffix,"test","user");
        jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)","it-admin-"+suffix,"test","admin");
        userId=jdbc.queryForObject("SELECT id FROM user WHERE userAccount=?",Long.class,"it-user-"+suffix);
        adminId=jdbc.queryForObject("SELECT id FROM user WHERE userAccount=?",Long.class,"it-admin-"+suffix);
        jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(1,?,?,0)","it-space-"+suffix,adminId);
        spaceId=jdbc.queryForObject("SELECT id FROM wiki_space WHERE name=?",Long.class,"it-space-"+suffix);
        jdbc.update("INSERT INTO wiki_space_user(spaceId,userId,spaceRole,isDelete) VALUES(?,?,?,0)",spaceId,userId,"editor");
        jdbc.update("INSERT INTO wiki_folder(spaceId,name,isDelete) VALUES(?,?,0)",spaceId,"it-parent-"+suffix);
        parentId=jdbc.queryForObject("SELECT id FROM wiki_folder WHERE name=?",Long.class,"it-parent-"+suffix);
        jdbc.update("INSERT INTO wiki_folder(spaceId,parentId,name,isDelete) VALUES(?,?,?,0)",spaceId,parentId,"it-child-"+suffix);
        folderId=jdbc.queryForObject("SELECT id FROM wiki_folder WHERE name=?",Long.class,"it-child-"+suffix);
        user=new User();user.setId(userId);user.setUserRole("user");admin=new User();admin.setId(adminId);admin.setUserRole("admin");
        FetchedPage page=new FetchedPage(); page.setFinalUrl("https://example.org/test");page.setPageTitle("test page");
        page.setHtml("<html><body><article><p>"+"正文内容".repeat(1800)+"</p></article></body></html>");
        when(fetcher.fetch(anyString())).thenReturn(page);
    }
    @Test void importSerializesWithMemberRemoval() throws Exception { race(()->spaces.removeTeamMember(spaceId,userId,admin),"member"); }
    @Test void importSerializesWithRoleChange() throws Exception { race(()->spaces.addTeamMember(spaceId,userId,"viewer",admin),"role"); }
    @Test void importSerializesWithMemberExit() throws Exception { race(()->spaces.exitTeamSpace(spaceId,user),"member"); }
    @Test void importSerializesWithTargetDeletion() throws Exception { race(()->folders.deleteFolderTree(folderId,user),"folder"); }
    @Test void importSerializesWithAncestorDeletion() throws Exception { race(()->folders.deleteFolderTree(parentId,user),"folder"); }
    @Test void importSerializesWithSpaceDeletion() throws Exception { race(()->spaces.deleteTeamSpace(spaceId,true,admin),"space"); }
    @Test void removalFirstRejectsBlockedImport() throws Exception { mutationFirst(()->spaces.removeTeamMember(spaceId,userId,admin)); }
    @Test void roleChangeFirstRejectsBlockedImport() throws Exception { mutationFirst(()->spaces.addTeamMember(spaceId,userId,"viewer",admin)); }
    @Test void exitFirstRejectsBlockedImport() throws Exception { mutationFirst(()->spaces.exitTeamSpace(spaceId,user)); }
    @Test void targetDeletionFirstRejectsBlockedImport() throws Exception { mutationFirst(()->folders.deleteFolderTree(folderId,user)); }
    @Test void ancestorDeletionFirstRejectsBlockedImport() throws Exception { mutationFirst(()->folders.deleteFolderTree(parentId,user)); }
    @Test void spaceDeletionFirstRejectsBlockedImport() throws Exception { mutationFirst(()->spaces.deleteTeamSpace(spaceId,true,admin)); }
    private void mutationFirst(Callable<?> mutation) throws Exception {
        CountDownLatch mutated=new CountDownLatch(1),release=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate tx=new TransactionTemplate(manager);
            tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
            Future<?> first=pool.submit(()->tx.execute(status->{
                try { mutation.call(); mutated.countDown(); if(!release.await(15,TimeUnit.SECONDS)) throw new IllegalStateException(); return null; }
                catch(Exception e) { throw new IllegalStateException(e); }
            }));
            assertTrue(mutated.await(15,TimeUnit.SECONDS));
            Future<Map<String,Object>> second=pool.submit(()->imports.importUrls(userId,spaceId,folderId,List.of("https://example.org/test")));
            Thread.sleep(200); assertFalse(second.isDone()); release.countDown(); first.get(15,TimeUnit.SECONDS);
            Map<?,?> item=(Map<?,?>)((List<?>)second.get(15,TimeUnit.SECONDS).get("items")).get(0);
            assertEquals("failed",item.get("status"));
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE spaceId=?",Long.class,spaceId));
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void folderMoveWaitsForImportAndKeepsDocumentInMovedFolder() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        doAnswer(inv->{entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS));return inv.callRealMethod();}).when(documents).save(any(DocumentWiki.class));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Map<String,Object>> first=pool.submit(()->imports.importUrls(userId,spaceId,folderId,List.of("https://example.org/test")));
            assertTrue(entered.await(15,TimeUnit.SECONDS));
            Future<?> second=pool.submit(()->folders.moveFolder(folderId,null,user));
            Thread.sleep(200); assertFalse(second.isDone()); release.countDown();
            Map<?,?> item=(Map<?,?>)((List<?>)first.get(15,TimeUnit.SECONDS).get("items")).get(0);
            assertEquals("success",item.get("status")); second.get(15,TimeUnit.SECONDS);
            assertNull(jdbc.queryForObject("SELECT parentId FROM wiki_folder WHERE id=?",Long.class,folderId));
            assertEquals(folderId,jdbc.queryForObject("SELECT folderId FROM document_wiki WHERE id=?",Long.class,Long.parseLong((String)item.get("documentId"))));
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void deletingInitiallyEmptySpaceAfterConcurrentRootImportDoesNotLeaveOrphan() throws Exception {
        jdbc.update("DELETE FROM wiki_folder WHERE spaceId=?",spaceId);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        doAnswer(inv->{entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS));return inv.callRealMethod();}).when(documents).save(any(DocumentWiki.class));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Map<String,Object>> first=pool.submit(()->imports.importUrls(userId,spaceId,null,List.of("https://example.org/test")));
            assertTrue(entered.await(15,TimeUnit.SECONDS));
            Future<?> second=pool.submit(()->spaces.deleteTeamSpace(spaceId,true,admin));
            Thread.sleep(200); assertFalse(second.isDone()); release.countDown();
            Map<?,?> item=(Map<?,?>)((List<?>)first.get(15,TimeUnit.SECONDS).get("items")).get(0);
            assertEquals("success",item.get("status")); second.get(15,TimeUnit.SECONDS);
            assertEquals(1,jdbc.queryForObject("SELECT isDelete FROM document_wiki WHERE id=?",Integer.class,Long.parseLong((String)item.get("documentId"))));
            assertEquals(1,jdbc.queryForObject("SELECT isDelete FROM wiki_space WHERE id=?",Integer.class,spaceId));
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void persistenceFailureNeverReportsSuccessOrRetries() throws Exception {
        doThrow(new org.springframework.dao.DeadlockLoserDataAccessException("simulated database deadlock",null))
                .when(documents).save(any(DocumentWiki.class));
        Map<String,Object> result=imports.importUrls(userId,spaceId,folderId,List.of("https://example.org/test","https://example.org/next"));
        List<?> items=(List<?>)result.get("items");
        assertEquals("unknown",((Map<?,?>)items.get(0)).get("status"));
        assertEquals("not_executed",((Map<?,?>)items.get(1)).get("status"));
        verify(documents,times(1)).save(any(DocumentWiki.class));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE spaceId=?",Long.class,spaceId));
    }
    private void race(Callable<?> mutation,String kind) throws Exception {
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        doAnswer(inv->{entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS));return inv.callRealMethod();}).when(documents).save(any(DocumentWiki.class));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Map<String,Object>> first=pool.submit(()->imports.importUrls(userId,spaceId,folderId,List.of("https://example.org/test")));
            assertTrue(entered.await(15,TimeUnit.SECONDS));
            CountDownLatch started=new CountDownLatch(1);
            Future<?> second=pool.submit(()->{started.countDown();return mutation.call();});
            assertTrue(started.await(5,TimeUnit.SECONDS)); Thread.sleep(200); assertFalse(second.isDone());
            release.countDown();
            Map<?,?> item=(Map<?,?>)((List<?>)first.get(15,TimeUnit.SECONDS).get("items")).get(0);
            assertEquals("success",item.get("status")); second.get(15,TimeUnit.SECONDS);
            long id=Long.parseLong((String)item.get("documentId"));
            assertTrue(jdbc.queryForObject("SELECT CHAR_LENGTH(content) FROM document_wiki WHERE id=?",Integer.class,id)>5000);
            if("member".equals(kind)) assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM wiki_space_user WHERE spaceId=? AND userId=? AND isDelete=0",Long.class,spaceId,userId));
            else if("role".equals(kind)) assertEquals("viewer",jdbc.queryForObject("SELECT spaceRole FROM wiki_space_user WHERE spaceId=? AND userId=? AND isDelete=0",String.class,spaceId,userId));
            else assertEquals(1,jdbc.queryForObject("SELECT isDelete FROM document_wiki WHERE id=?",Integer.class,id));
            Map<?,?> after=(Map<?,?>)((List<?>)imports.importUrls(userId,spaceId,folderId,List.of("https://example.org/test")).get("items")).get(0);
            assertEquals("failed",after.get("status"));
            assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE spaceId=?",Long.class,spaceId));
        } finally {release.countDown();pool.shutdownNow();}
    }

    @Test void folderRenameFirstInvalidatesConfirmedTarget() throws Exception { confirmedMutationFirst(() -> folders.renameFolder(parentId,"renamed-parent",user)); }
    @Test void spaceRenameFirstInvalidatesConfirmedTarget() throws Exception { confirmedMutationFirst(() -> spaces.renameSpace(spaceId,"renamed-space",admin)); }
    @Test void folderMoveFirstInvalidatesConfirmedTarget() throws Exception { confirmedMutationFirst(() -> folders.moveFolder(folderId,null,user)); }
    @Test void folderRenameWaitsForConfirmedWrite() throws Exception { confirmedWriteFirst(() -> folders.renameFolder(parentId,"renamed-parent",user)); }
    @Test void spaceRenameWaitsForConfirmedWrite() throws Exception { confirmedWriteFirst(() -> spaces.renameSpace(spaceId,"renamed-space",admin)); }
    @Test void folderMoveWaitsForConfirmedWrite() throws Exception { confirmedWriteFirst(() -> folders.moveFolder(folderId,null,user)); }
    private AgentUrlImportService.TargetSnapshot targetSnapshot() {
        String spaceName=jdbc.queryForObject("SELECT name FROM wiki_space WHERE id=?",String.class,spaceId);
        String parentName=jdbc.queryForObject("SELECT name FROM wiki_folder WHERE id=?",String.class,parentId);
        String folderName=jdbc.queryForObject("SELECT name FROM wiki_folder WHERE id=?",String.class,folderId);
        return new AgentUrlImportService.TargetSnapshot("团队「"+spaceName+"」",List.of(parentId+":"+parentName,folderId+":"+folderName));
    }
    private void confirmedMutationFirst(Callable<?> mutation) throws Exception {
        var snapshot=targetSnapshot(); CountDownLatch changed=new CountDownLatch(1),release=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate tx=new TransactionTemplate(manager); tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
            Future<?> first=pool.submit(()->tx.execute(status->{try {mutation.call(); changed.countDown(); if(!release.await(15,TimeUnit.SECONDS)) throw new IllegalStateException(); return null;} catch(Exception e){throw new IllegalStateException(e);}}));
            assertTrue(changed.await(15,TimeUnit.SECONDS));
            Future<Map<String,Object>> second=pool.submit(()->imports.importUrls(userId,spaceId,folderId,List.of("https://example.org/test"),snapshot));
            Thread.sleep(200); assertFalse(second.isDone()); release.countDown(); first.get(15,TimeUnit.SECONDS);
            Map<?,?> item=(Map<?,?>)((List<?>)second.get(15,TimeUnit.SECONDS).get("items")).get(0);
            assertEquals("failed",item.get("status"));
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE spaceId=?",Long.class,spaceId));
        } finally {release.countDown();pool.shutdownNow();}
    }
    private void confirmedWriteFirst(Callable<?> mutation) throws Exception {
        var snapshot=targetSnapshot(); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),started=new CountDownLatch(1);
        doAnswer(inv->{entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS));return inv.callRealMethod();}).when(documents).save(any(DocumentWiki.class));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Map<String,Object>> first=pool.submit(()->imports.importUrls(userId,spaceId,folderId,List.of("https://example.org/test"),snapshot));
            assertTrue(entered.await(15,TimeUnit.SECONDS));
            Future<?> second=pool.submit(()->{started.countDown();return mutation.call();});
            assertTrue(started.await(5,TimeUnit.SECONDS)); Thread.sleep(200); assertFalse(second.isDone()); release.countDown();
            Map<?,?> item=(Map<?,?>)((List<?>)first.get(15,TimeUnit.SECONDS).get("items")).get(0);
            assertEquals("success",item.get("status")); second.get(15,TimeUnit.SECONDS);
            assertEquals(folderId,jdbc.queryForObject("SELECT folderId FROM document_wiki WHERE id=?",Long.class,Long.parseLong((String)item.get("documentId"))));
            assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE spaceId=?",Long.class,spaceId));
        } finally {release.countDown();pool.shutdownNow();}
    }

    @Test void folderRenameRemainsCommittedWhenCacheClearFails() {
        doThrow(new IllegalStateException("mock cache failure")).when(cache).clearSpace(spaceId);
        assertThrows(IllegalStateException.class,()->folders.renameFolder(parentId,"renamed-cache-failure",user));
        assertEquals("renamed-cache-failure",jdbc.queryForObject("SELECT name FROM wiki_folder WHERE id=?",String.class,parentId));
    }
    @Test void spaceRenameRemainsCommittedWhenCacheClearFails() {
        doThrow(new IllegalStateException("mock cache failure")).when(cache).clearSpace(spaceId);
        assertThrows(IllegalStateException.class,()->spaces.renameSpace(spaceId,"renamed-cache-failure",admin));
        assertEquals("renamed-cache-failure",jdbc.queryForObject("SELECT name FROM wiki_space WHERE id=?",String.class,spaceId));
    }
}

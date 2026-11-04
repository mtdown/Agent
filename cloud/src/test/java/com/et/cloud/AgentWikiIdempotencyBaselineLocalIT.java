package com.et.cloud;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.et.cloud.mapper.WikiSpaceMapper;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiFolder;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.*;
import com.et.cloud.service.impl.AgentWikiCreationService;
import com.et.cloud.service.impl.DocumentWikiServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Local baseline: real MySQL/MyBatis transaction; HTTP and permission adapters are test-only. */
class AgentWikiIdempotencyBaselineLocalIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static String schema, password, marker;
    private static long userId, spaceId, folderId;

    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.et.cloud.mapper")
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/" + schema + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", "awib_test", password);
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            com.baomidou.mybatisplus.core.MybatisConfiguration config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(false);
            config.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
            factory.setConfiguration(config);
            return factory.getObject();
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean UserService users() {
            UserService result = mock(UserService.class);
            User user = new User(); user.setId(userId); user.setUserRole("user"); user.setIsDelete(0);
            when(result.getById(anyLong())).thenAnswer(inv -> inv.<Long>getArgument(0) == userId ? user : null);
            return result;
        }
        @Bean WikiSpaceService spaces() {
            WikiSpaceService result = mock(WikiSpaceService.class);
            WikiSpace space = new WikiSpace(); space.setId(spaceId); space.setOwnerUserId(userId); space.setType(0); space.setIsDelete(0);
            when(result.requireSpaceAccess(eq(spaceId), any(User.class), eq(true))).thenReturn(space);
            return result;
        }
        @Bean WikiFolderService folders() {
            WikiFolderService result = mock(WikiFolderService.class);
            WikiFolder folder = new WikiFolder(); folder.setId(folderId); folder.setSpaceId(spaceId); folder.setName("baseline"); folder.setIsDelete(0);
            when(result.requireFolderAccess(eq(folderId), eq(spaceId), any(User.class), eq(true))).thenReturn(folder);
            return result;
        }
        @Bean WikiCacheManager cache() { return mock(WikiCacheManager.class); }
        @Bean DocumentWikiServiceImpl documents() { return new DocumentWikiServiceImpl(); }
        @Bean AgentWikiCreationService creation(UserService u, WikiSpaceService s, WikiSpaceMapper m,
                WikiFolderService f, DocumentWikiService d, WikiCacheManager c) {
            return new AgentWikiCreationService(u, s, m, f, d, c);
        }
    }

    @Test void baseline() throws Exception {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
                .setLevel(ch.qos.logback.classic.Level.WARN);
        schema = required("AWIB_SCHEMA"); password = required("AWIB_DB_PASSWORD");
        assertTrue(schema.matches("awib_[a-f0-9]{12}"));
        String expectedUuid = required("AWIB_DB_UUID");
        String owner = required("AWIB_DB_MARKER");
        String key = required("AWIB_INTERNAL_KEY");
        marker = "awib-" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(Boolean.parseBoolean(required("AWIB_INTERNAL_NETWORK_VERIFIED")));
        String url = "jdbc:mysql://127.0.0.1:3306/" + schema + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection c = DriverManager.getConnection(url, "awib_test", password); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT @@server_uuid, DATABASE(), (SELECT marker FROM baseline_owner_marker)")) {
                assertTrue(rs.next()); assertEquals(expectedUuid, rs.getString(1)); assertEquals(schema, rs.getString(2)); assertEquals(owner, rs.getString(3));
            }
            String ddl = Files.readString(projectRoot().resolve("deploy/schema-init.sql"), StandardCharsets.UTF_8);
            for (String table : List.of("user", "wiki_space", "wiki_folder", "document_wiki")) {
                Matcher m = Pattern.compile("(?s)CREATE TABLE `" + table + "` \\(.*?\\) ENGINE=.*?;").matcher(ddl);
                assertTrue(m.find(), "missing DDL " + table);
                s.execute(m.group().replaceFirst("CREATE TABLE", "CREATE TABLE IF NOT EXISTS"));
            }
        }
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "awib_test", password));
        String run = marker;
        jdbc.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)", run, "synthetic-only", "user");
        userId = jdbc.queryForObject("SELECT id FROM user WHERE userAccount=?", Long.class, run);
        jdbc.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)", run, userId);
        spaceId = jdbc.queryForObject("SELECT id FROM wiki_space WHERE name=?", Long.class, run);
        jdbc.update("INSERT INTO wiki_folder(spaceId,name,isDelete) VALUES(?,?,0)", spaceId, run);
        folderId = jdbc.queryForObject("SELECT id FROM wiki_folder WHERE spaceId=? AND name=?", Long.class, spaceId, run);
        String content = Files.readString(Path.of(required("AWIB_SAMPLE_PATH")), StandardCharsets.UTF_8);
        assertFalse(content.isBlank());
        Path reportDir = projectRoot().resolve("docs/issue-reports/agent-wiki-write-idempotency");
        Path agentRecord = reportDir.resolve(run + "-agent.csv");
        Path dbRecord = reportDir.resolve(run + "-db.csv");
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class)) {
            AgentWikiCreationService service = ctx.getBean(AgentWikiCreationService.class);
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/awib-test-identity", x -> { byte[] b = run.getBytes(StandardCharsets.UTF_8); x.sendResponseHeaders(200, b.length); x.getResponseBody().write(b); x.close(); });
            server.createContext("/api/internal/agent/wiki-documents/create", x -> {
                try {
                    if (!key.equals(x.getRequestHeaders().getFirst("X-Agent-Key"))) { x.sendResponseHeaders(403, -1); return; }
                    JsonNode b = JSON.readTree(x.getRequestBody());
                    AgentWikiCreationService.DocumentResult result = service.createDocument(userId, b.path("spaceId").asText(), b.path("folderId").asText(), b.path("title").asText(), b.path("content").asText());
                    byte[] out = JSON.writeValueAsBytes(Map.of("code", 0, "data", result));
                    x.sendResponseHeaders(200, out.length); x.getResponseBody().write(out);
                } catch (Exception e) {
                    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                        System.err.println("AWIB_ADAPTER_CAUSE=" + cause.getClass().getName()
                                + (cause instanceof SQLException ? ":SQLSTATE=" + ((SQLException) cause).getSQLState() : ""));
                    }
                    x.sendResponseHeaders(500, -1);
                }
                finally { x.close(); }
            });
            server.setExecutor(Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; }));
            server.start();
            try {
                ProcessBuilder pb = new ProcessBuilder("mvn", "-o", "-q", "-f", "cloud_agent/pom.xml", "-Dtest=AgentWikiBaselineAgentIT", "test");
                pb.directory(projectRoot().toFile());
                pb.environment().put("AWIB_CLOUD_BASE", "http://127.0.0.1:" + server.getAddress().getPort());
                pb.environment().put("AWIB_USER_ID", Long.toString(userId)); pb.environment().put("AWIB_SPACE_ID", Long.toString(spaceId));
                pb.environment().put("AWIB_FOLDER_ID", Long.toString(folderId)); pb.environment().put("AWIB_RUN_MARKER", run);
                pb.environment().put("AWIB_AGENT_RECORD_PATH", agentRecord.toString());
                pb.redirectErrorStream(true); pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                Process agent = pb.start();
                assertTrue(agent.waitFor(15, TimeUnit.MINUTES)); assertEquals(0, agent.exitValue());
                List<String> lines = Files.readAllLines(agentRecord, StandardCharsets.UTF_8); assertEquals(31, lines.size());
                StringBuilder records = new StringBuilder("scenario,group,document_count\n");
                for (int i = 0; i < 30; i++) {
                    long n = count(jdbc, run + "-natural-" + i); assertEquals(1, n); records.append("natural,").append(i).append(',').append(n).append('\n');
                }
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                for (int i = 0; i < 30; i++) {
                    String title = run + "-replay-" + i; String body = body(title, content);
                    send(http, server, body, key); send(http, server, body, key);
                    long n = count(jdbc, title); records.append("manual_replay,").append(i).append(',').append(n).append('\n');
                }
                ExecutorService pool = Executors.newFixedThreadPool(2);
                try {
                    for (int i = 0; i < 30; i++) {
                        String title = run + "-concurrent-" + i; String body = body(title, content);
                        CountDownLatch start = new CountDownLatch(1);
                        Future<?> a = pool.submit(() -> { start.await(); send(http, server, body, key); return null; });
                        Future<?> b = pool.submit(() -> { start.await(); send(http, server, body, key); return null; });
                        start.countDown(); a.get(); b.get();
                        long n = count(jdbc, title); records.append("manual_concurrent,").append(i).append(',').append(n).append('\n');
                    }
                } finally { pool.shutdownNow(); }
                Files.writeString(dbRecord, records.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } finally { server.stop(0); }
        }
    }

    private static long count(JdbcTemplate jdbc, String title) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE spaceId=? AND folderId=? AND (title=? OR title LIKE ?)", Long.class, spaceId, folderId, title, title + "（%）");
    }
    private static String body(String title, String content) throws Exception {
        return JSON.writeValueAsString(Map.of("spaceId", Long.toString(spaceId), "folderId", Long.toString(folderId), "title", title, "content", content));
    }
    private static void send(HttpClient client, HttpServer server, String body, String key) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/internal/agent/wiki-documents/create");
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("X-Agent-Key", key).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); assertEquals(0, JSON.readTree(response.body()).path("code").asInt(-1));
    }
    private static String required(String name) {
        String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name); return value;
    }
    private static Path projectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        return Files.exists(cwd.resolve("deploy/schema-init.sql")) ? cwd : cwd.getParent();
    }
}

package com.et.cloud;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

final class LocalFullTestSchema {
    private static String schemaName, schemaToken, serverUuid, jdbcUrl, username, password;
    private static int mysqlPort, serverPort;
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry properties) throws Exception {
        username = requiredEnv("WIKI_IT_MYSQL_USER");
        password = requiredEnv("WIKI_IT_MYSQL_PASSWORD");
        String port = requiredEnv("WIKI_IT_MYSQL_PORT");
        if (!port.matches("[0-9]{1,5}")) throw new IllegalStateException("Invalid local test MySQL port");
        mysqlPort = Integer.parseInt(port);
        if (mysqlPort < 1 || mysqlPort > 65535) throw new IllegalStateException("Invalid local test MySQL port");
        schemaToken = UUID.randomUUID().toString().replace("-", "");
        schemaName = "codex_full_it_" + schemaToken;
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
        Path files = Files.createTempDirectory("codex-full-it-files-");
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
    static void assertSchema(Connection connection, boolean markerRequired) throws SQLException {
        if (schemaName == null || !schemaName.matches("codex_full_it_[0-9a-f]{32}")) throw new IllegalStateException("Invalid test schema identity");
        assertServer(connection);
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT DATABASE(), @@server_uuid")) {
            if (!rs.next() || !schemaName.equals(rs.getString(1)) || !serverUuid.equals(rs.getString(2))) throw new IllegalStateException("Unexpected test schema or server identity");
        }
        if (markerRequired) try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM codex_test_owner WHERE token=?")) {
            statement.setString(1, schemaToken);
            try (ResultSet rs = statement.executeQuery()) { if (!rs.next() || rs.getInt(1) != 1) throw new IllegalStateException("Test schema ownership marker missing"); }
        }
    }
    static synchronized void cleanup() throws Exception {
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
}

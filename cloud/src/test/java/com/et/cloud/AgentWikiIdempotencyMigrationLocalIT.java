package com.et.cloud;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AgentWikiIdempotencyMigrationLocalIT {
    @Test void migrationIsRepeatableAndEnforcesUserOperationUniqueness() throws Exception {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path root = Files.exists(cwd.resolve("deploy/schema-init.sql")) ? cwd : cwd.getParent();
        Path migration = root.resolve("cloud/sql/create_table_agent_wiki_operation.sql");
        assertTrue(Files.isRegularFile(migration), "idempotency migration must exist");
        String schema = required("AWIB_SCHEMA"), password = required("AWIB_DB_PASSWORD");
        assertTrue(schema.matches("awib_[a-f0-9]{12}"));
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertFalse(sql.toUpperCase().contains("DROP "));
        assertTrue(Boolean.parseBoolean(required("AWIB_INTERNAL_NETWORK_VERIFIED")));
        String url="jdbc:mysql://127.0.0.1:3306/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection c = DriverManager.getConnection(url,"awib_test",password); Statement s=c.createStatement()) {
            try(ResultSet rs=s.executeQuery("SELECT @@server_uuid,DATABASE(),(SELECT marker FROM baseline_owner_marker)")) {
                assertTrue(rs.next()); assertEquals(required("AWIB_DB_UUID"),rs.getString(1)); assertEquals(schema,rs.getString(2)); assertEquals(required("AWIB_DB_MARKER"),rs.getString(3));
            }
            s.execute(sql);
            String id=UUID.randomUUID().toString(); long user=Math.abs(UUID.randomUUID().getLeastSignificantBits() >>> 2)+1;
            try(PreparedStatement p=c.prepareStatement("INSERT INTO agent_wiki_operation(user_id,operation_id,request_version,request_hash,space_id,folder_id,document_id) VALUES(?,?,1,?,1,1,1)")) {
                p.setLong(1,user); p.setString(2,id); p.setString(3,"a".repeat(64)); assertEquals(1,p.executeUpdate());
                assertThrows(SQLException.class,p::executeUpdate);
                p.setLong(1,user+1); assertEquals(1,p.executeUpdate());
                p.setLong(1,user); p.setString(2,UUID.randomUUID().toString()); assertEquals(1,p.executeUpdate());
            }
            s.execute(sql);
            try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM agent_wiki_operation WHERE user_id=? AND operation_id=?")) {
                p.setLong(1,user); p.setString(2,id); try(ResultSet r=p.executeQuery()){assertTrue(r.next());assertEquals(1,r.getInt(1));}
            }
            try(ResultSet r=c.getMetaData().getColumns(null,null,"agent_wiki_operation",null)) {
                int count=0; while(r.next()){count++; assertFalse(r.getString("COLUMN_NAME").toLowerCase().contains("content"));} assertTrue(count>0);
            }
        }
    }
    private static String required(String n){String v=System.getenv(n);if(v==null||v.isBlank())throw new IllegalStateException("Missing "+n);return v;}
}

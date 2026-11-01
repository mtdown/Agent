package com.et.cloud;

import com.et.cloud.rag.WikiRagIndexService;
import com.et.cloud.service.WikiCacheManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@SpringBootTest
@ActiveProfiles("local")
class CloudApplicationTests {
    @MockBean WikiCacheManager cache;
    @MockBean WikiRagIndexService rag;

    @DynamicPropertySource static void isolatedProperties(DynamicPropertyRegistry registry) throws Exception {
        LocalFullTestSchema.mysql(registry);
        registry.add("spring.session.store-type", () -> "none");
    }
    @AfterAll static void cleanup() throws Exception { LocalFullTestSchema.cleanup(); }

    @TestConfiguration
    static class GuardedDataSourceConfig {
        @Bean @Primary DataSource guardedDataSource(@Value("${spring.datasource.url}") String url,
                                                  @Value("${spring.datasource.username}") String user,
                                                  @Value("${spring.datasource.password}") String password) {
            DriverManagerDataSource delegate = new DriverManagerDataSource(url, user, password);
            return new DelegatingDataSource(delegate) {
                @Override public Connection getConnection() throws SQLException {
                    return verify(super.getConnection());
                }
                @Override public Connection getConnection(String username, String password) throws SQLException {
                    return verify(super.getConnection(username, password));
                }
                private Connection verify(Connection connection) throws SQLException {
                    try { LocalFullTestSchema.assertSchema(connection, true); return connection; }
                    catch (RuntimeException | SQLException e) { connection.close(); throw e; }
                }
            };
        }
    }
    @Test void contextLoads() {}
}

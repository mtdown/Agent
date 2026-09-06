package com.et.cloud.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenApiLocalProfileTest {

    @Test
    void localProfileDisablesKnife4jBasicAuthForCodeGeneration() throws IOException {
        Map<String, Object> config;
        try (InputStream inputStream = Files.newInputStream(Path.of("src/main/resources/application-local.yml"))) {
            config = new Yaml().load(inputStream);
        }

        Map<?, ?> knife4j = (Map<?, ?>) config.get("knife4j");
        assertNotNull(knife4j);
        Map<?, ?> basic = (Map<?, ?>) knife4j.get("basic");
        assertNotNull(basic);
        assertEquals(Boolean.FALSE, basic.get("enable"));
    }

    @Test
    void defaultProfileDoesNotRequireDeprecatedAliyunApiKey() throws IOException {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(application.contains("apiKey: ${ALIYUN_AI_API_KEY:}"));
    }

    @Test
    void localProfileDocumentsRetiredSharding() throws IOException {
        String localProfile = Files.readString(Path.of("src/main/resources/application-local.yml"));

        assertTrue(localProfile.contains("ShardingSphere is retired"));
        assertTrue(localProfile.contains("not enabled"));
    }
}

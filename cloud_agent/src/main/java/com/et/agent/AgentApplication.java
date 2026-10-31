package com.et.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.util.Collections;

@SpringBootApplication
public class AgentApplication {
    public static void main(String[] args) { SpringApplication.run(AgentApplication.class, args); }

    @Bean AgentLoop loop(@Value("${agent.model.base-url}") String baseUrl,
                         @Value("${agent.model.api-key}") String apiKey,
                         @Value("${agent.model.name}") String model,
                         @Value("${agent.timeout-ms}") long timeoutMs,
                         @Value("${agent.max-turns}") int maxTurns,
                         @Value("${agent.max-tool-result-chars}") int maxResultChars,
                         @Value("${agent.web.api-key:}") String webKey) {
        return new AgentLoop(new OpenAiStreamModel(baseUrl, apiKey, model, timeoutMs),
                Collections.emptyMap(), maxTurns, timeoutMs, maxResultChars).withWeb(new DeepSeekWebResearch(webKey));
    }

}

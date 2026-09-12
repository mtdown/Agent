package com.et.cloud.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG configuration bound from the "rag" yml section. All secrets come from
 * environment variables with empty defaults so startup never blocks.
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Embedding embedding = new Embedding();

    private Llm llm = new Llm();

    private Retrieval retrieval = new Retrieval();

    private Index index = new Index();

    @Data
    public static class Llm {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        /** 深度思考模式使用的思考型模型 */
        private String model = "deepseek-v4-flash-vision-exp";
        /** 默认（快速）模式使用的非思考模型，秒级响应 */
        private String fastModel = "deepseek-chat";
        private int timeoutSeconds = 120;
        // 思考型模型 reasoning 与正文共用预算，需留足空间，避免 finishReason=length 截断
        private int maxTokens = 8192;

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }

        /** deepThinking=true 用思考型模型，否则用快速模型；快速模型未配置时回退思考型 */
        public String resolveModel(boolean deepThinking) {
            if (!deepThinking) {
                if (fastModel != null && !fastModel.isBlank()) {
                    return fastModel;
                }
            }
            return model;
        }
    }

    @Data
    public static class Embedding {
        // 与 application.yml 默认保持一致：云端 DashScope；本地 Ollama 需显式覆盖 base-url
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String apiKey = "";
        private String model = "qwen3.7-text-embedding-flash";
        private int timeoutSeconds = 30;

        public boolean isConfigured() {
            if (apiKey != null && !apiKey.isBlank()) {
                return true;
            }
            // 本地 OpenAI 兼容端点（如 Ollama）无需 api-key
            return isLocalBaseUrl();
        }

        private boolean isLocalBaseUrl() {
            if (baseUrl == null || baseUrl.isBlank()) {
                return false;
            }
            String host = baseUrl.replace("http://", "").replace("https://", "");
            return host.startsWith("localhost") || host.startsWith("127.0.0.1");
        }
    }

    @Data
    public static class Retrieval {
        private int topK = 6;
    }

    @Data
    public static class Index {
        private int batchEmbedSize = 10;
        private int asyncCorePoolSize = 2;
        private int asyncMaxPoolSize = 4;
        private int asyncQueueCapacity = 200;
    }
}

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
        /**
         * 生成模型是否开思考（DashScope 系模型对应请求体的 enable_thinking）。
         * null = 不发送该字段，保持各厂商默认行为（DeepSeek 链路因此完全不受影响）；
         * 仅在显式配置 true/false 时才写入请求体。实测 qwen3.8-max 默认开思考。
         */
        private Boolean enableThinking;

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }

        /** 是否需要把 enable_thinking 写进请求体（未显式配置时为 false） */
        public boolean hasEnableThinking() {
            return enableThinking != null;
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
        /** Number of hits returned to the caller when the request does not specify one. */
        private int topK = 6;

        /**
         * Hard ceiling for the requested topK. Without it a single request can
         * ask for the whole space, paying a full brute-force scan plus a huge
         * payload — and diluting the prompt downstream.
         */
        private int topKMax = 50;

        /**
         * Coarse-retrieval candidate pool per channel. The re-ranker can only
         * reorder what is already in the pool, so pool depth caps achievable
         * recall — but only up to a point: measured over the eval set with the
         * dense+BM25 hybrid pool, a perfect re-ranker could take recall@6 to
         * 0.7690 / 0.8600 / 0.9061 at depth 20 / 50 / 100, while the shipped
         * system actually reached 0.7590 / 0.7930 / 0.8030. The re-ranker's
         * precision decays as the pool grows (98.7% → 92.2% → 88.6% of the
         * ceiling), so depth 100 buys 1.0pt for +190ms over depth 50.
         * 50 is the default knee; override with RAG_CANDIDATE_POOL_SIZE.
         */
        private int candidatePoolSize = 50;

        /** Size of the fused pool handed to the re-ranker (and used when re-ranking is off). */
        private int fusionPoolSize = 50;

        /**
         * RRF constant. Left at the literature default on purpose: it is NOT
         * tuned against the eval set, so the reported gain stays honest.
         */
        private int rrfK = 60;

        private Lexical lexical = new Lexical();

        private Rerank rerank = new Rerank();

        private MultiQuery multiQuery = new MultiQuery();

        private EvidenceAssembly evidenceAssembly = new EvidenceAssembly();
    }

    @Data
    public static class MultiQuery {
        private boolean enabled = false;
        private String model = "qwen3.8-flash";
        private int timeoutSeconds = 20;
        private int maxTokens = 512;
        private double originalWeight = 1.0d;
        private double rewrittenQuestionWeight = 0.7d;
        private double hypotheticalAnswerWeight = 0.6d;
    }

    @Data
    public static class EvidenceAssembly {
        private boolean enabled = false;
        private int maxMergedChars = 3600;
        private int maxChunksPerBlock = 3;
        private int maxChunkIndexGap = 1;
        private int maxPerDocument = 3;
        private int finalContextK = 6;
    }

    @Data
    public static class Lexical {
        private boolean enabled = true;
        /** BM25 term-frequency saturation. */
        private double k1 = 1.2;
        /** BM25 length normalisation. */
        private double b = 0.75;
    }

    @Data
    public static class Rerank {
        private boolean enabled = true;
        /** DashScope native text-rerank endpoint (NOT the OpenAI-compatible base-url). */
        private String baseUrl = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
        private String apiKey = "";
        /**
         * Cross-encoder model. {@code qwen3.7-text-rerank} shares the DashScope
         * key already used for embeddings; it separates a relevant document from
         * an irrelevant one far more sharply than {@code gte-rerank-v2} on the
         * same input (top score 0.9973 vs 0.5897). The {@code qwen3-reranker-*}
         * names are NOT served on this key — they answer "Model not exist".
         */
        private String model = "qwen3.7-text-rerank";
        private int timeoutSeconds = 20;
        /** Upper bound of documents sent per call; deeper pools are truncated. */
        private int maxDocuments = 50;

        /** Re-ranking stays off until a key is supplied — never a hard failure. */
        public boolean isUsable() {
            return enabled && apiKey != null && !apiKey.isBlank();
        }
    }

    @Data
    public static class Index {
        private int batchEmbedSize = 10;
        private int asyncCorePoolSize = 2;
        private int asyncMaxPoolSize = 4;
        private int asyncQueueCapacity = 200;
    }
}

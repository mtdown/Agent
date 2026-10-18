package com.et.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** OpenAI-compatible SSE chat adapter; accepts optional tool-call fragments. */
public final class OpenAiStreamModel implements AgentLoop.ToolAwareModel {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "agent-model-deadline");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient client;
    private final String baseUrl, apiKey, model;
    private final long timeoutMs;

    public OpenAiStreamModel(String baseUrl, String apiKey, String model, long timeoutMs) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUrl = baseUrl; this.apiKey = apiKey; this.model = model; this.timeoutMs = timeoutMs;
    }

    @Override public AgentLoop.Turn stream(List<AgentLoop.Message> history, Consumer<String> delta, BooleanSupplier cancelled) {
        return stream(history, Collections.emptySet(), delta, cancelled);
    }

    @Override public AgentLoop.Turn stream(List<AgentLoop.Message> history, Set<String> toolNames,
                                           Consumer<String> delta, BooleanSupplier cancelled) {
        if (apiKey == null || apiKey.isEmpty()) throw new IllegalStateException("Agent model API key is not configured");
        long started = System.nanoTime();
        AtomicBoolean timedOut = new AtomicBoolean();
        ScheduledFuture<?> deadline = null;
        try {
            ObjectNode body = JSON.createObjectNode();
            body.put("model", model); body.put("stream", true);
            body.put("max_tokens", 2048);
            if (model.startsWith("deepseek-")) body.putObject("thinking").put("type", "disabled");
            body.putObject("stream_options").put("include_usage", true);
            ArrayNode declaredTools = body.putArray("tools");
            for (String name : toolNames) declaredTools.add(AgentToolRegistry.declaration(name));
            if (declaredTools.isEmpty()) body.remove("tools");
            ArrayNode messages = body.putArray("messages");
            for (AgentLoop.Message entry : history) {
                ObjectNode message = messages.addObject();
                message.put("role", entry.role); message.put("content", entry.content);
                if (entry.toolCallId != null) message.put("tool_call_id", entry.toolCallId);
                if (!entry.toolCalls.isEmpty()) {
                    ArrayNode calls = message.putArray("tool_calls");
                    for (AgentLoop.ToolCall call : entry.toolCalls) {
                        ObjectNode node = calls.addObject();
                        node.put("id", call.id); node.put("type", "function");
                        ObjectNode function = node.putObject("function");
                        function.put("name", call.name); function.put("arguments", call.arguments);
                    }
                }
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
            HttpResponse<java.util.stream.Stream<String>> response = client.send(request, HttpResponse.BodyHandlers.ofLines());
            try (java.util.stream.Stream<String> lines = response.body()) {
                if (response.statusCode() != 200) throw new IllegalStateException("Model HTTP " + response.statusCode());
                long remaining = Math.max(1, timeoutMs - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                deadline = DEADLINES.schedule(() -> {
                    timedOut.set(true);
                    lines.close();
                }, remaining, TimeUnit.MILLISECONDS);
                Parser parser = new Parser(delta);
                Iterator<String> iterator = lines.iterator();
                while (iterator.hasNext()) {
                    if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new AgentLoop.Cancelled();
                    parser.accept(iterator.next());
                }
                if (timedOut.get()) throw new IllegalStateException("Model stream timed out after " + timeoutMs + " ms");
                return parser.finish();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new AgentLoop.Cancelled();
        } catch (AgentLoop.Cancelled | IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            if (timedOut.get()) throw new IllegalStateException("Model stream timed out after " + timeoutMs + " ms", e);
            throw new IllegalStateException("Model stream failed: " + e.getMessage(), e);
        } finally {
            if (deadline != null) deadline.cancel(false);
        }
    }

    public static final class Parser {
        private final Consumer<String> delta;
        private final Map<Integer, StringBuilder[]> fragments = new TreeMap<>();
        private AgentLoop.Usage usage;
        private boolean ended;
        private String finishReason;
        public Parser(Consumer<String> delta) { this.delta = delta; }
        public void accept(String line) {
            if (line == null || !line.startsWith("data:")) return;
            String data = line.substring(5).trim();
            if ("[DONE]".equals(data)) { ended = true; return; }
            try {
                JsonNode root = JSON.readTree(data);
                JsonNode tokens = root.path("usage");
                if (tokens.isObject() && tokens.hasNonNull("prompt_tokens") && tokens.hasNonNull("completion_tokens"))
                    usage = new AgentLoop.Usage(tokens.path("prompt_tokens").asLong(), tokens.path("completion_tokens").asLong());
                for (JsonNode choice : root.path("choices")) {
                    if (choice.hasNonNull("finish_reason")) finishReason = choice.path("finish_reason").asText();
                    JsonNode change = choice.path("delta");
                    if (change.hasNonNull("content") && !change.path("content").asText().isEmpty())
                        delta.accept(change.path("content").asText());
                    for (JsonNode call : change.path("tool_calls")) {
                        int index = call.path("index").asInt(-1);
                        if (index < 0) throw new IllegalArgumentException("Tool call index missing");
                        StringBuilder[] parts = fragments.computeIfAbsent(index,
                                key -> new StringBuilder[]{new StringBuilder(), new StringBuilder(), new StringBuilder()});
                        append(parts[0], call.path("id"));
                        append(parts[1], call.path("function").path("name"));
                        append(parts[2], call.path("function").path("arguments"));
                    }
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Malformed model SSE event", e);
            }
        }
        private static void append(StringBuilder target, JsonNode node) {
            if (!node.isMissingNode() && !node.isNull()) target.append(node.asText());
        }
        public AgentLoop.Turn finish() {
            if (!ended && finishReason == null) throw new IllegalStateException("Model stream ended without terminal marker");
            List<AgentLoop.ToolCall> calls = new ArrayList<>();
            for (StringBuilder[] parts : fragments.values())
                calls.add(new AgentLoop.ToolCall(parts[0].toString(), parts[1].toString(), parts[2].toString()));
            return new AgentLoop.Turn(calls, usage);
        }
    }
}

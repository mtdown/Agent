package com.et.cloud.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.function.BiConsumer;

/** Copies private Agent SSE bytes promptly and closes upstream on browser disconnect. */
@Component
public class AgentPreviewProxy {
    /** The browser only receives the result of projecting internal Wiki events. */
    public interface WikiEvents {
        PublicEvent project(String event, JsonNode data);
        String interactionJson();
        default String displayAnswer() { return null; }
    }
    public static final class PublicEvent {
        public final String name;
        public final Object data;
        public PublicEvent(String name, Object data) { this.name = name; this.data = data; }
    }
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl, internalKey;
    public AgentPreviewProxy(@Value("${agent.base-url:http://127.0.0.1:8124}") String baseUrl,
                             @Value("${agent.internal-key:}") String internalKey) {
        this.baseUrl = baseUrl; this.internalKey = internalKey;
    }
    public StreamingResponseBody stream(String query) {
        return stream(java.util.Collections.singletonMap("query", query), null, null);
    }
    public StreamingResponseBody stream(Map<String, Object> payload, Map<String, Object> meta,
                                        BiConsumer<Boolean, String[]> completed) {
        return stream(payload, meta, completed, null);
    }
    public StreamingResponseBody stream(Map<String, Object> payload, Map<String, Object> meta,
                                        BiConsumer<Boolean, String[]> completed, WikiEvents wikiEvents) {
        long startedAt = System.nanoTime();
        return out -> {
            StringBuilder answer = new StringBuilder();
            String[] finalStats = {null};
            boolean success = false;
            if (internalKey == null || internalKey.isEmpty()) {
                sendError(out, "Agent service credential is not configured");
                if (completed != null) completed.accept(false, new String[]{"", null});
                return;
            }
            try {
                if (meta != null) writeFrame(out, "event: meta\ndata: " + JSON.writeValueAsString(meta) + "\n\n");
                byte[] requestBody = JSON.writeValueAsBytes(payload);
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/agent/stream"))
                        .timeout(Duration.ofMinutes(3))
                        .header("X-Agent-Key", internalKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream source = response.body();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
                    if (response.statusCode() != 200) {
                        sendError(out, "Agent service returned HTTP " + response.statusCode()); return;
                    }
                    StringBuilder frame = new StringBuilder();
                    String event = null;
                    Long firstAnswerMs = null;
                    String line;
                    while (!Thread.currentThread().isInterrupted() && (line = reader.readLine()) != null) {
                        if (!line.isEmpty()) {
                            frame.append(line).append('\n');
                            if (line.startsWith("event:")) event = line.substring(6).trim();
                            continue;
                        }
                        if (frame.length() == 0) continue;
                        if ("delta".equals(event) && firstAnswerMs == null) {
                            firstAnswerMs = elapsedMs(startedAt);
                        }
                        if ("done".equals(event)) {
                            String safeAnswer = wikiEvents == null ? null : wikiEvents.displayAnswer();
                            if (safeAnswer != null) {
                                answer.setLength(0);
                                answer.append(safeAnswer);
                                writeFrame(out, "event: delta\ndata: " + JSON.writeValueAsString(
                                        java.util.Collections.singletonMap("content", safeAnswer)) + "\n\n");
                            }
                            String internalInteraction = wikiEvents == null ? null : wikiEvents.interactionJson();
                            if (internalInteraction != null) {
                                Map<String, Object> display = AgentWikiChoiceService.safeDisplay(internalInteraction, false);
                                if ("wiki_write_result".equals(display.get("kind"))
                                        || "wiki_partial_result".equals(display.get("kind")))
                                    writeFrame(out, "event: wiki_result\ndata: " + JSON.writeValueAsString(display) + "\n\n");
                            }
                            String data = null;
                            for (String part : frame.toString().split("\n")) {
                                if (part.startsWith("data:")) data = part.substring(5).trim();
                            }
                            ObjectNode done = (ObjectNode) JSON.readTree(data);
                            if (firstAnswerMs == null) done.putNull("firstAnswerMs");
                            else done.put("firstAnswerMs", firstAnswerMs);
                            done.put("totalMs", elapsedMs(startedAt));
                            finalStats[0] = JSON.writeValueAsString(done);
                            success = true;
                            writeFrame(out, "event: done\ndata: " + JSON.writeValueAsString(done) + "\n\n");
                        } else if (event != null && event.startsWith("wiki_")) {
                            // Internal Agent tool data contains real IDs. Never forward that frame.
                            if (wikiEvents != null) {
                                String data = null;
                                for (String part : frame.toString().split("\n"))
                                    if (part.startsWith("data:")) data = part.substring(5).trim();
                                if (data == null) throw new IllegalStateException("Wiki event has no data");
                                PublicEvent projected = wikiEvents.project(event, JSON.readTree(data));
                                if (projected != null)
                                    writeFrame(out, "event: " + projected.name + "\ndata: "
                                            + JSON.writeValueAsString(projected.data) + "\n\n");
                            }
                        } else {
                            if ("delta".equals(event)) {
                                for (String part : frame.toString().split("\n")) if (part.startsWith("data:")) {
                                    com.fasterxml.jackson.databind.JsonNode node = JSON.readTree(part.substring(5).trim());
                                    if (node.has("content")) answer.append(node.get("content").asText());
                                }
                            }
                            writeFrame(out, frame.toString() + '\n');
                        }
                        frame.setLength(0);
                        event = null;
                    }
                }
            } catch (java.io.IOException e) {
                // A browser disconnect makes write/flush fail; closing source above cancels the upstream read.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                sendError(out, "Agent service unavailable");
            } finally {
                if (completed != null) completed.accept(success, new String[]{answer.toString(), finalStats[0],
                        wikiEvents == null ? null : wikiEvents.interactionJson()});
            }
        };
    }
    private static long elapsedMs(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
    private static void writeFrame(java.io.OutputStream out, String frame) throws java.io.IOException {
        out.write(frame.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
    private static void sendError(java.io.OutputStream out, String message) throws java.io.IOException {
        String frame = "event: error\ndata: " + JSON.writeValueAsString(java.util.Collections.singletonMap("message", message)) + "\n\n";
        writeFrame(out, frame);
    }
}

package com.et.cloud.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Copies private Agent SSE bytes promptly and closes upstream on browser disconnect. */
@Component
public class AgentPreviewProxy {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl, internalKey;
    public AgentPreviewProxy(@Value("${agent.base-url:http://127.0.0.1:8124}") String baseUrl,
                             @Value("${agent.internal-key:}") String internalKey) {
        this.baseUrl = baseUrl; this.internalKey = internalKey;
    }
    public StreamingResponseBody stream(String query) {
        long startedAt = System.nanoTime();
        return out -> {
            if (internalKey == null || internalKey.isEmpty()) {
                sendError(out, "Agent service credential is not configured"); return;
            }
            try {
                byte[] requestBody = JSON.writeValueAsBytes(java.util.Collections.singletonMap("query", query));
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
                            String data = null;
                            for (String part : frame.toString().split("\n")) {
                                if (part.startsWith("data:")) data = part.substring(5).trim();
                            }
                            ObjectNode done = (ObjectNode) JSON.readTree(data);
                            if (firstAnswerMs == null) done.putNull("firstAnswerMs");
                            else done.put("firstAnswerMs", firstAnswerMs);
                            done.put("totalMs", elapsedMs(startedAt));
                            writeFrame(out, "event: done\ndata: " + JSON.writeValueAsString(done) + "\n\n");
                        } else {
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

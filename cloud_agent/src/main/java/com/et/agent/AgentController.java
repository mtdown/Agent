package com.et.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/internal/agent")
public final class AgentController {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AgentLoop loop;
    private final String secret;
    public AgentController(AgentLoop loop, @Value("${agent.internal-key}") String secret) { this.loop = loop; this.secret = secret; }
    public static final class Ask {
        public String query;
        public java.util.List<History> history;
        public String evidence;
        public Ask() { }
        public Ask(String query) { this.query = query; }
    }
    public static final class History {
        public String role;
        public String content;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody stream(@RequestHeader(value = "X-Agent-Key", required = false) String key,
                                        @RequestBody Ask ask) {
        if (secret == null || secret.isEmpty()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        if (key == null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (ask == null || ask.query == null || ask.query.trim().isEmpty() || ask.query.length() > 4000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return out -> {
            AtomicBoolean disconnected = new AtomicBoolean(false);
            try {
                java.util.List<AgentLoop.Message> prior = new java.util.ArrayList<>();
                if (ask.history != null) for (History item : ask.history) {
                    if (item != null && ("user".equals(item.role) || "assistant".equals(item.role)) && item.content != null)
                        prior.add(new AgentLoop.Message(item.role, item.content, null, null));
                }
                AgentLoop.Result result = loop.run(ask.query, prior, ask.evidence, chunk -> {
                    try { send(out, "delta", one("content", chunk)); }
                    catch (IOException e) { disconnected.set(true); throw new UncheckedIOException(e); }
                }, disconnected::get);
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("usage", result.usage);
                done.put("firstAnswerMs", result.firstAnswerMs);
                done.put("totalMs", result.totalMs);
                send(out, "done", done);
            } catch (UncheckedIOException | AgentLoop.Cancelled e) {
                // Downstream closed: stop work without attempting another frame.
            } catch (Exception e) {
                send(out, "error", one("message", e.getMessage() == null ? "Agent request failed" : e.getMessage()));
            }
        };
    }
    private static Map<String, Object> one(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>(); map.put(key, value); return map;
    }
    private static void send(OutputStream out, String event, Object data) throws IOException {
        String frame = "event: " + event + "\ndata: " + JSON.writeValueAsString(data) + "\n\n";
        out.write(frame.getBytes(StandardCharsets.UTF_8)); out.flush();
    }
}

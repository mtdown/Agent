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
    private final AgentLibraryGateway library;
    public AgentController(AgentLoop loop, @Value("${agent.internal-key}") String secret,
                           AgentLibraryGateway library) {
        this.loop = loop; this.secret = secret; this.library = library;
    }
    public static final class Ask {
        public String query;
        public java.util.List<History> history;
        public String evidence;
        public String userId;
        public String skillCatalog;
        public String selectedSpaceId;
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
        if (ask.userId == null || !ask.userId.matches("[1-9][0-9]{0,18}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (ask.selectedSpaceId != null && !ask.selectedSpaceId.matches("[1-9][0-9]{0,18}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return out -> {
            AtomicBoolean disconnected = new AtomicBoolean(false);
            try {
                java.util.List<AgentLoop.Message> prior = new java.util.ArrayList<>();
                if (ask.history != null) for (History item : ask.history) {
                    if (item != null && ("user".equals(item.role) || "assistant".equals(item.role)) && item.content != null)
                        prior.add(new AgentLoop.Message(item.role, item.content, null, null));
                }
                Map<String, AgentLoop.Tool> tools = AgentToolRegistry.bind(ask.userId, library,
                        ask.selectedSpaceId);
                String query = ask.selectedSpaceId == null ? ask.query : ask.query +
                        "\n\n[用户已点击确认本次 Wiki 写入空间。先调用 plan_wiki_destination 获取本次确认的位置引用，再用其创建文件夹和文档；创建工具会拒绝其他空间。]";
                AgentLoop.Result result = loop.run(query, prior, ask.evidence, ask.skillCatalog, tools, chunk -> {
                    try { send(out, "delta", one("content", chunk)); }
                    catch (IOException e) { disconnected.set(true); throw new UncheckedIOException(e); }
                }, disconnected::get);
                if (result.choiceRequest != null)
                    send(out, "wiki_choice_request", JSON.readTree(result.choiceRequest));
                for (AgentLoop.ToolEvent item : result.wikiEvents) {
                    if ("plan_wiki_destination".equals(item.name)) continue;
                    if ("personal_file_reference".equals(item.name)) {
                        send(out, "personal_file_reference", JSON.readTree(item.result));
                        continue;
                    }
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("name", item.name);
                    event.put("result", JSON.readTree(item.result));
                    send(out, "wiki_tool_event", event);
                }
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("usage", result.usage);
                done.put("firstAnswerMs", result.firstAnswerMs);
                done.put("totalMs", result.totalMs);
                done.put("toolTraces", result.toolTraces);
                send(out, "done", done);
            } catch (UncheckedIOException | AgentLoop.Cancelled e) {
                // Downstream closed: stop work without attempting another frame.
            } catch (Exception e) {
                send(out, "error", one("message", "Agent request failed"));
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

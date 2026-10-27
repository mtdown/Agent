package com.et.cloud.controller;

import com.et.cloud.commen.BaseResponse;
import com.et.cloud.commen.ResultUtils;
import com.et.cloud.model.entity.User;
import com.et.cloud.rag.*;
import com.et.cloud.service.UserService;
import com.et.cloud.service.WikiSpaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Authenticated Cloud entry for private Agent conversations and permission-filtered retrieval. */
@RestController
@RequestMapping("/agent")
public class AgentPreviewController {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final UserService users;
    private final AgentPreviewProxy proxy;
    private final AgentConversationStore store;
    private final RagSearchService search;
    private final WikiSpaceService spaces;
    private final AgentSkillCatalog skillCatalog;
    private final AgentWikiChoiceService choices;

    @Autowired
    public AgentPreviewController(UserService users, AgentPreviewProxy proxy, AgentConversationStore store,
                                  RagSearchService search, WikiSpaceService spaces, AgentSkillCatalog skillCatalog,
                                  AgentWikiChoiceService choices) {
        this.users = users; this.proxy = proxy; this.store = store; this.search = search; this.spaces = spaces;
        this.skillCatalog = skillCatalog; this.choices = choices;
    }

    /** Kept for the existing direct controller tests. Spring uses the injected constructor above. */
    AgentPreviewController(UserService users, AgentPreviewProxy proxy, AgentConversationStore store,
                           RagSearchService search, WikiSpaceService spaces, AgentSkillCatalog skillCatalog) {
        this(users, proxy, store, search, spaces, skillCatalog, null);
    }

    public static class Ask {
        public String query;
        public String conversationId;
        public String ragMode = "OFF";
        public List<String> spaceIds;
        public List<String> folderIds;
        public Ask() { }
        public Ask(String query) { this.query = query; }
    }

    @GetMapping("/conversations")
    public BaseResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        return ResultUtils.success(store.list(users.getLoginUser(request).getId()));
    }

    @PostMapping("/conversations")
    public BaseResponse<String> create(HttpServletRequest request) {
        return ResultUtils.success(Long.toString(store.create(users.getLoginUser(request).getId())));
    }

    @GetMapping("/conversations/{id}")
    public BaseResponse<List<Map<String, Object>>> messages(@PathVariable String id, HttpServletRequest request) {
        return ResultUtils.success(store.messages(users.getLoginUser(request).getId(), Long.parseLong(id)));
    }

    @GetMapping("/conversations/{id}/wiki-documents/{turnId}/preview")
    public BaseResponse<Map<String, String>> previewWikiResult(@PathVariable String id, @PathVariable String turnId,
                                                                @RequestParam String token, HttpServletRequest request) {
        if (!id.matches("[1-9][0-9]{0,18}") || !turnId.matches("[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("Invalid document action");
        return ResultUtils.success(choices.preview(users.getLoginUser(request), Long.parseLong(id),
                Long.parseLong(turnId), token));
    }

    @DeleteMapping("/conversations/{id}")
    public BaseResponse<Boolean> delete(@PathVariable String id, HttpServletRequest request) {
        store.delete(users.getLoginUser(request).getId(), Long.parseLong(id));
        return ResultUtils.success(true);
    }

    public static class Rename {
        public String title;
    }

    @PatchMapping("/conversations/{id}")
    public BaseResponse<Boolean> rename(@PathVariable String id, @RequestBody Rename body, HttpServletRequest request) {
        store.rename(users.getLoginUser(request).getId(), Long.parseLong(id), body == null ? null : body.title);
        return ResultUtils.success(true);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody stream(@RequestBody Ask ask, HttpServletRequest request) {
        return streamInternal(ask, request, null);
    }

    public static class WikiChoice {
        public String turnId;
        public String token;
    }

    @PostMapping(value = "/conversations/{id}/wiki-choice/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody selectWikiChoice(@PathVariable String id, @RequestBody WikiChoice choice,
                                                  HttpServletRequest request) {
        if (choice == null || choice.turnId == null || !choice.turnId.matches("[1-9][0-9]{0,18}") ||
                id == null || !id.matches("[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("Invalid Wiki choice");
        User user = users.getLoginUser(request);
        AgentWikiChoiceService.Selection selected = choices.claim(user, Long.parseLong(id),
                Long.parseLong(choice.turnId), choice.token);
        Ask resumed = new Ask(selected.originalQuery);
        resumed.conversationId = id;
        return streamInternal(resumed, request, selected.spaceId);
    }

    private StreamingResponseBody streamInternal(Ask ask, HttpServletRequest request, String selectedSpaceId) {
        User user = users.getLoginUser(request);
        if (ask == null || ask.query == null || ask.query.trim().isEmpty() || ask.query.length() > 4000)
            throw new IllegalArgumentException("Invalid question");
        long conversationId = ask.conversationId == null ? store.create(user.getId()) : Long.parseLong(ask.conversationId);
        store.requireOwner(user.getId(), conversationId);
        String mode = ask.ragMode == null ? "OFF" : ask.ragMode;
        if (!Arrays.asList("OFF", "ALL", "SELECTED").contains(mode)) throw new IllegalArgumentException("Invalid RAG mode");

        List<Long> selected = new ArrayList<>();
        List<Long> selectedFolders = new ArrayList<>();
        if ("SELECTED".equals(mode)) {
            if ((ask.spaceIds == null || ask.spaceIds.isEmpty()) &&
                    (ask.folderIds == null || ask.folderIds.isEmpty()))
                throw new IllegalArgumentException("Select at least one space or folder");
            Set<Long> visible = new HashSet<>(spaces.listVisibleSpaceIds(user));
            if (ask.spaceIds != null) {
                for (String id : ask.spaceIds) {
                    long parsed = Long.parseLong(id);
                    if (visible.contains(parsed) && !selected.contains(parsed)) selected.add(parsed);
                }
            }
            if (ask.folderIds != null) {
                for (String id : ask.folderIds) {
                    long parsed = Long.parseLong(id);
                    if (!selectedFolders.contains(parsed)) selectedFolders.add(parsed);
                }
            }
        }

        List<RagCitation> citations = new ArrayList<>();
        String evidence = null;
        RagSearchResult result = null;
        if (!"OFF".equals(mode) && !("SELECTED".equals(mode) && selected.isEmpty() && selectedFolders.isEmpty())) {
            RagSearchRequest requestSearch = new RagSearchRequest();
            requestSearch.setQuery(ask.query);
            requestSearch.setSpaceIds("ALL".equals(mode) ? null : selected);
            requestSearch.setFolderIds("ALL".equals(mode) ? null : selectedFolders);
            requestSearch.setTopK(6);
            result = search.search(user, requestSearch);
            StringBuilder builder = new StringBuilder();
            int index = 1;
            for (ChunkHit hit : result.getHits()) {
                RagCitation citation = new RagCitation();
                citation.setIndex(index);
                citation.setDocId(hit.getDocId());
                citation.setDocTitle(hit.getDocTitle());
                citation.setDocNumber(hit.getDocNumber());
                citation.setChunkIndex(hit.getChunkIndex());
                citation.setChunkHeading(hit.getChunkHeading());
                citation.setChunkText(hit.getChunkText() == null ? null : hit.getChunkText().substring(0, Math.min(1500, hit.getChunkText().length())));
                citations.add(citation);
                builder.append('[').append(index++).append("] ").append(hit.getDocTitle()).append(" ")
                        .append(hit.getDocNumber() == null ? "" : hit.getDocNumber()).append("\n")
                        .append(citation.getChunkText()).append("\n\n");
            }
            evidence = builder.toString();
        }

        List<Map<String, Object>> history = store.recentCompleted(user.getId(), conversationId);
        Map<String, Object> scopeSnapshot = new LinkedHashMap<>();
        scopeSnapshot.put("spaceIds", ask.spaceIds == null ? Collections.emptyList() : ask.spaceIds);
        scopeSnapshot.put("folderIds", ask.folderIds == null ? Collections.emptyList() : ask.folderIds);
        String idsJson = json(scopeSnapshot);
        long turnId = store.begin(user.getId(), conversationId, ask.query, mode, idsJson);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("conversationId", Long.toString(conversationId));
        meta.put("citations", citations);
        meta.put("effectiveSpaceIds", result == null ? Collections.emptyList() : result.getEffectiveSpaceIds().stream().map(String::valueOf).collect(Collectors.toList()));
        meta.put("authorizedDocCount", result == null ? 0 : result.getAuthorizedDocCount());
        String citationsJson = json(citations);
        if (!"OFF".equals(mode) && citations.isEmpty()) {
            return out -> {
                String answer = "所选空间中未找到可支持回答的依据。";
                String stats = "{\"usage\":null,\"firstAnswerMs\":null,\"totalMs\":0}";
                try {
                    frame(out, "meta", meta);
                    frame(out, "delta", Collections.singletonMap("content", answer));
                    frame(out, "done", JSON.readTree(stats));
                    store.complete(user.getId(), conversationId, turnId, answer, citationsJson, stats);
                } catch (java.io.IOException e) { store.fail(user.getId(), conversationId, turnId); throw e; }
            };
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", ask.query);
        payload.put("history", history);
        payload.put("evidence", evidence);
        payload.put("userId", Long.toString(user.getId()));
        payload.put("skillCatalog", skillCatalog.forUser(user.getId()));
        if (selectedSpaceId != null) payload.put("selectedSpaceId", selectedSpaceId);
        AgentWikiChoiceProjection projection = choices == null ? null :
                new AgentWikiChoiceProjection(choices, user, conversationId, turnId);
        java.util.function.BiConsumer<Boolean, String[]> completed = (success, output) -> {
            if (success) {
                String interaction = output.length > 2 ? output[2] : null;
                String answer = interaction == null ? output[0] : "请选择写入空间";
                store.complete(user.getId(), conversationId, turnId, answer, citationsJson, output[1], interaction);
            } else store.fail(user.getId(), conversationId, turnId);
        };
        return projection == null ? proxy.stream(payload, meta, completed) :
                proxy.stream(payload, meta, completed, projection);
    }

    private static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static void frame(java.io.OutputStream out, String event, Object data) throws java.io.IOException {
        out.write(("event: " + event + "\ndata: " + json(data) + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}

package com.et.cloud.controller;

import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.WikiSpaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps Wiki destination IDs server-side while the browser uses one-use choice tokens. */
@Service
public class AgentWikiChoiceService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long TTL_MILLIS = 30L * 60L * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final AgentWritableWikiLocations directory;
    private final WikiSpaceService spaces;
    private final DocumentWikiService documents;

    public AgentWikiChoiceService(DataSource dataSource, AgentWritableWikiLocations directory,
                                  WikiSpaceService spaces, DocumentWikiService documents) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.directory = directory;
        this.spaces = spaces;
        this.documents = documents;
    }

    public static final class Prepared {
        public final Map<String, Object> display;
        public final String internalJson;
        Prepared(Map<String, Object> display, String internalJson) {
            this.display = display;
            this.internalJson = internalJson;
        }
    }

    public static final class Selection {
        public final String originalQuery, spaceId, label;
        Selection(String originalQuery, String spaceId, String label) {
            this.originalQuery = originalQuery;
            this.spaceId = spaceId;
            this.label = label;
        }
    }

    public Prepared prepare(User owner, long conversationId, long turnId, JsonNode request) {
        if (owner == null || owner.getId() == null || request == null ||
                !request.path("choiceRequired").asBoolean(false) || !request.path("candidates").isArray())
            throw new IllegalArgumentException("Invalid Wiki destination choice");
        Map<String, Object> fresh = directory.list(owner.getId(), null, null, null);
        Map<String, Map<?, ?>> writable = new HashMap<>();
        for (Object raw : (List<?>) fresh.get("spaces")) {
            Map<?, ?> space = (Map<?, ?>) raw;
            writable.put(String.valueOf(space.get("id")), space);
        }
        ObjectNode internal = JSON.createObjectNode();
        internal.put("kind", "destination_choice");
        internal.put("ownerId", owner.getId().toString());
        internal.put("conversationId", Long.toString(conversationId));
        internal.put("turnId", Long.toString(turnId));
        long expiresAt = Instant.now().toEpochMilli() + TTL_MILLIS;
        internal.put("expiresAt", expiresAt);
        ArrayNode storedOptions = internal.putArray("options");
        List<Map<String, Object>> displayOptions = new ArrayList<>();
        Map<String, Integer> labelCounts = new HashMap<>();
        for (JsonNode candidate : request.path("candidates")) {
            String id = candidate.path("id").asText("");
            Map<?, ?> space = writable.remove(id);
            if (space == null) continue;
            String label = displayLabel(String.valueOf(space.get("type")), String.valueOf(space.get("name")));
            int count = labelCounts.merge(label, 1, Integer::sum);
            if (count > 1) label += "（选项 " + count + "）";
            byte[] random = new byte[32];
            RANDOM.nextBytes(random);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            boolean recommended = id.equals(request.path("recommendedSpaceId").asText(""));
            storedOptions.addObject().put("token", token).put("spaceId", id)
                    .put("label", label).put("recommended", recommended);
            Map<String, Object> shown = new LinkedHashMap<>();
            shown.put("token", token);
            shown.put("label", label);
            shown.put("recommended", recommended);
            displayOptions.add(shown);
            if (displayOptions.size() >= 50) break;
        }
        if (displayOptions.isEmpty()) throw new IllegalArgumentException("No writable destination is available");
        Map<String, Object> display = new LinkedHashMap<>();
        display.put("kind", "destination_choice");
        display.put("turnId", Long.toString(turnId));
        display.put("expiresAt", expiresAt);
        display.put("options", displayOptions);
        return new Prepared(display, internal.toString());
    }

    @Transactional
    public Selection claim(User owner, long conversationId, long turnId, String token) {
        if (owner == null || owner.getId() == null || token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("Invalid or expired choice");
        List<Integer> conversations = jdbc.query("SELECT busy FROM agent_conversation WHERE id=? " +
                        "AND ownerUserId=? AND isDelete=0 FOR UPDATE", (rs, row) -> rs.getInt(1),
                conversationId, owner.getId());
        if (conversations.size() != 1 || conversations.get(0) != 0)
            throw new IllegalArgumentException("Conversation is unavailable");
        Long latestUserTurn = jdbc.queryForObject("SELECT MAX(id) FROM agent_message WHERE conversationId=? " +
                "AND role='user'", Long.class, conversationId);
        if (latestUserTurn == null || latestUserTurn != turnId)
            throw new IllegalArgumentException("Choice is no longer current");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, interactionJson, choiceConsumed " +
                "FROM agent_message WHERE conversationId=? AND turnId=? AND role='assistant' " +
                "AND status='DONE' AND interactionJson IS NOT NULL", conversationId, turnId);
        if (rows.size() != 1 || ((Number) rows.get(0).get("choiceConsumed")).intValue() != 0)
            throw new IllegalArgumentException("Invalid or expired choice");
        final JsonNode internal;
        try { internal = JSON.readTree(String.valueOf(rows.get(0).get("interactionJson"))); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid choice state", e); }
        if (!"destination_choice".equals(internal.path("kind").asText()) ||
                !owner.getId().toString().equals(internal.path("ownerId").asText()) ||
                !Long.toString(conversationId).equals(internal.path("conversationId").asText()) ||
                !Long.toString(turnId).equals(internal.path("turnId").asText()) ||
                internal.path("expiresAt").asLong(0) <= Instant.now().toEpochMilli())
            throw new IllegalArgumentException("Invalid or expired choice");
        JsonNode selected = null;
        for (JsonNode option : internal.path("options")) {
            if (MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                    option.path("token").asText("").getBytes(StandardCharsets.UTF_8))) {
                selected = option;
                break;
            }
        }
        if (selected == null) throw new IllegalArgumentException("Invalid or expired choice");
        String spaceId = selected.path("spaceId").asText();
        spaces.requireSpaceAccess(Long.parseLong(spaceId), owner, true);
        if (jdbc.update("UPDATE agent_message SET choiceConsumed=1 WHERE id=? AND choiceConsumed=0",
                rows.get(0).get("id")) != 1)
            throw new IllegalArgumentException("Choice was already used");
        String original = jdbc.queryForObject("SELECT content FROM agent_message WHERE id=? " +
                "AND conversationId=? AND role='user' AND status='DONE'", String.class,
                turnId, conversationId);
        return new Selection(original, spaceId, selected.path("label").asText());
    }

    public static String displayLabel(String type, String name) {
        if ("personal".equals(type)) return "个人空间";
        if ("public".equals(type)) return "公开空间";
        if ("team".equals(type)) return "团队「" + name + "」";
        throw new IllegalArgumentException("Unknown Wiki space type");
    }

    /** Resolve a fresh, permission-checked label without exposing the storage name. */
    public String displayLabelFor(User owner, String spaceId) {
        if (owner == null || owner.getId() == null || spaceId == null) throw new IllegalArgumentException("Invalid space");
        Map<String, Object> fresh = directory.list(owner.getId(), null, null, null);
        for (Object raw : (List<?>) fresh.get("spaces")) {
            Map<?, ?> space = (Map<?, ?>) raw;
            if (spaceId.equals(String.valueOf(space.get("id"))))
                return displayLabel(String.valueOf(space.get("type")), String.valueOf(space.get("name")));
        }
        throw new IllegalArgumentException("Wiki destination is no longer writable");
    }

    /** Authorization is checked in the same database read as the space metadata, without cached memberships. */
    private Map<Long, Map<String,Object>> readableSpaces(User owner, java.util.Collection<Long> ids) {
        List<Long> values = new ArrayList<>(new java.util.LinkedHashSet<>(ids));
        Map<Long,Map<String,Object>> readable = new HashMap<>();
        if (owner == null || owner.getId() == null) return readable;
        for (int offset = 0; offset < values.size(); offset += 50) {
            List<Long> batch = values.subList(offset, Math.min(values.size(), offset + 50));
            String marks = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            List<Object> params = new ArrayList<>(); params.add(owner.getId()); params.add(owner.getId()); params.add(owner.getId()); params.addAll(batch);
            String sql = "SELECT s.id,s.type,s.name FROM wiki_space s LEFT JOIN wiki_space_user m ON m.spaceId=s.id AND m.userId=? AND m.isDelete=0 " +
                    "WHERE s.isDelete=0 AND (s.type=2 OR (s.type=0 AND s.ownerUserId=?) OR (s.type=1 AND m.userId IS NOT NULL) " +
                    "OR EXISTS (SELECT 1 FROM user u WHERE u.id=? AND u.isDelete=0 AND u.userRole='admin')) AND s.id IN ("+marks+")";
            for (Map<String,Object> row : jdbc.queryForList(sql,params.toArray())) readable.put(((Number)row.get("id")).longValue(),row);
        }
        return readable;
    }
    private static String labelForSpace(Map<String,Object> row) {
        int type=((Number)row.get("type")).intValue();
        return displayLabel(type==WikiSpaceService.TYPE_PERSONAL ? "personal" : type==WikiSpaceService.TYPE_TEAM ? "team" : "public",String.valueOf(row.get("name")));
    }

    /** Resolve document locations in bounded SQL batches after checking each current space permission. */
    public Map<String, Map<String, String>> resolveDocuments(User owner, java.util.Collection<String> ids) {
        Map<String, Map<String, String>> resolved = new LinkedHashMap<>();
        if (owner == null || owner.getId() == null || ids == null || ids.isEmpty()) return resolved;
        List<Long> parsed = new ArrayList<>();
        for (String id : new java.util.LinkedHashSet<>(ids)) {
            try { long value = Long.parseLong(id); if (value > 0) parsed.add(value); }
            catch (RuntimeException ignored) { }
        }
        Map<Long, Map<String, Object>> docs = new LinkedHashMap<>();
        for (int offset = 0; offset < parsed.size(); offset += 50) {
            List<Long> batch = parsed.subList(offset, Math.min(parsed.size(), offset + 50));
            String marks = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            for (Map<String, Object> row : jdbc.queryForList("SELECT id,spaceId,folderId,title FROM document_wiki WHERE isDelete=0 AND id IN (" + marks + ")", batch.toArray()))
                docs.put(((Number) row.get("id")).longValue(), row);
        }
        List<Long> docSpaces = new ArrayList<>();
        for (Map<String,Object> doc : docs.values()) if (doc.get("spaceId") instanceof Number) docSpaces.add(((Number)doc.get("spaceId")).longValue());
        Map<Long,Map<String,Object>> readable = readableSpaces(owner,docSpaces);
        Map<Long,String> labels = new HashMap<>(); Map<Long,Boolean> authorized = new HashMap<>();
        for (Long sid : docSpaces) { authorized.put(sid,readable.containsKey(sid)); if (readable.containsKey(sid)) labels.put(sid,labelForSpace(readable.get(sid))); }
        Map<Long, Map<String, Object>> folders = new HashMap<>();
        java.util.Set<Long> sought = new java.util.HashSet<>();
        java.util.Set<Long> pending = new java.util.HashSet<>();
        for (Map<String, Object> doc : docs.values()) {
            Number space = (Number) doc.get("spaceId"), folder = (Number) doc.get("folderId");
            if (space != null && Boolean.TRUE.equals(authorized.get(space.longValue())) && folder != null) pending.add(folder.longValue());
        }
        for (int depth = 0; depth < 256 && !pending.isEmpty(); depth++) {
            List<Long> current = new ArrayList<>();
            for (Long id : pending) if (sought.add(id)) current.add(id);
            pending.clear();
            for (int offset = 0; offset < current.size(); offset += 50) {
                List<Long> batch = current.subList(offset, Math.min(current.size(), offset + 50));
                String marks = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
                for (Map<String, Object> row : jdbc.queryForList("SELECT id,spaceId,parentId,name FROM wiki_folder WHERE isDelete=0 AND id IN (" + marks + ")", batch.toArray())) {
                    long id = ((Number) row.get("id")).longValue(); folders.put(id, row);
                    Number parent = (Number) row.get("parentId");
                    if (parent != null && !sought.contains(parent.longValue())) pending.add(parent.longValue());
                }
            }
        }
        for (Map.Entry<Long, Map<String, Object>> entry : docs.entrySet()) {
            Map<String, Object> doc = entry.getValue(); Number space = (Number) doc.get("spaceId");
            if (space == null || !Boolean.TRUE.equals(authorized.get(space.longValue()))) continue;
            List<String> path = new ArrayList<>(); java.util.Set<Long> visited = new java.util.HashSet<>();
            Number next = (Number) doc.get("folderId"); boolean valid = true;
            while (next != null) {
                long id = next.longValue(); Map<String, Object> folder = folders.get(id);
                if (visited.size() >= 256 || !visited.add(id) || folder == null ||
                        ((Number) folder.get("spaceId")).longValue() != space.longValue()) { valid = false; break; }
                path.add(String.valueOf(folder.get("name"))); next = (Number) folder.get("parentId");
            }
            Map<String, String> shown = new LinkedHashMap<>();
            shown.put("spaceLabel", labels.get(space.longValue()));
            shown.put("documentTitle", String.valueOf(doc.get("title")));
            if (valid) { java.util.Collections.reverse(path); shown.put("folderName", String.join(" / ", path)); }
            else shown.put("folderName", "路径不可用");
            shown.put("validPath", Boolean.toString(valid));
            resolved.put(Long.toString(entry.getKey()), shown);
        }
        return resolved;
    }

    /** Rebuild visible result fields using current database state and current access. */
    public Map<String, Object> currentDisplay(User owner, String internalJson, boolean consumed) {
        try {
            JsonNode state = JSON.readTree(internalJson);
            String kind = state.path("kind").asText();
            if ("destination_choice".equals(kind)) return safeDisplay(internalJson, consumed);
            List<JsonNode> documentItems = new ArrayList<>();
            if (state.path("documents").isArray()) state.path("documents").forEach(documentItems::add);
            else if ("wiki_write_result".equals(kind)) documentItems.add(state);
            List<String> ids = new ArrayList<>();
            for (JsonNode item : documentItems) ids.add(item.path("documentId").asText());
            Map<String, Map<String, String>> docs = resolveDocuments(owner, ids);
            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode item : documentItems) {
                Map<String, Object> row = new LinkedHashMap<>();
                Map<String, String> current = docs.get(item.path("documentId").asText());
                if (current == null) { row.put("kind", "wiki_unavailable_result"); }
                else {
                    row.put("kind", "wiki_write_result"); row.putAll(current);
                    if ("true".equals(current.get("validPath"))) row.put("openToken", item.path("openToken").asText());
                }
                row.put("turnId", state.path("turnId").asText()); result.add(row);
            }
            List<JsonNode> folderItems = new ArrayList<>();
            if (state.path("folders").isArray()) state.path("folders").forEach(folderItems::add);
            else if ("wiki_partial_result".equals(kind)) folderItems.add(state);
            List<Long> folderIds = new ArrayList<>();
            for (JsonNode item : folderItems) try { folderIds.add(Long.parseLong(item.path("folderId").asText())); } catch (RuntimeException ignored) { }
            Map<Long, Map<String,Object>> folderRows = new HashMap<>();
            for (int offset=0; offset<folderIds.size(); offset+=50) {
                List<Long> batch=folderIds.subList(offset, Math.min(folderIds.size(), offset+50));
                String marks=String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
                for (Map<String,Object> row : jdbc.queryForList("SELECT id,spaceId,parentId,name FROM wiki_folder WHERE isDelete=0 AND id IN ("+marks+")", batch.toArray()))
                    folderRows.put(((Number)row.get("id")).longValue(), row);
            }
            java.util.Set<Long> soughtFolders = new java.util.HashSet<>(folderIds);
            java.util.Set<Long> pendingParents = new java.util.HashSet<>();
            for (Map<String,Object> row : folderRows.values()) if (row.get("parentId") instanceof Number) pendingParents.add(((Number)row.get("parentId")).longValue());
            for (int depth=1; depth<256 && !pendingParents.isEmpty(); depth++) {
                List<Long> batchIds = new ArrayList<>();
                for (Long id : pendingParents) if (soughtFolders.add(id)) batchIds.add(id);
                pendingParents.clear();
                for (int offset=0; offset<batchIds.size(); offset+=50) {
                    List<Long> batch=batchIds.subList(offset,Math.min(batchIds.size(),offset+50));
                    String marks=String.join(",",java.util.Collections.nCopies(batch.size(),"?"));
                    for (Map<String,Object> row : jdbc.queryForList("SELECT id,spaceId,parentId,name FROM wiki_folder WHERE isDelete=0 AND id IN ("+marks+")",batch.toArray())) {
                        folderRows.put(((Number)row.get("id")).longValue(),row);
                        if (row.get("parentId") instanceof Number) pendingParents.add(((Number)row.get("parentId")).longValue());
                    }
                }
            }
            List<Long> folderSpaces = new ArrayList<>();
            for (Map<String,Object> row : folderRows.values()) folderSpaces.add(((Number)row.get("spaceId")).longValue());
            Map<Long,Map<String,Object>> allowed = readableSpaces(owner,folderSpaces);
            for (JsonNode item : folderItems) {
                Map<String,Object> shown = new LinkedHashMap<>();
                Map<String,Object> row = null;
                try { row=folderRows.get(Long.parseLong(item.path("folderId").asText())); } catch (RuntimeException ignored) { }
                if (row != null) {
                    long sid=((Number)row.get("spaceId")).longValue();
                    if (allowed.containsKey(sid)) {
                        shown.put("kind","wiki_folder_result"); shown.put("spaceLabel",labelForSpace(allowed.get(sid)));
                        List<String> path = new ArrayList<>(); java.util.Set<Long> visited = new java.util.HashSet<>();
                        Map<String,Object> cursor = row; boolean valid = true;
                        while (cursor != null) {
                            long fid=((Number)cursor.get("id")).longValue();
                            if (visited.size() >= 256 || !visited.add(fid) || ((Number)cursor.get("spaceId")).longValue()!=sid) { valid=false; break; }
                            path.add(String.valueOf(cursor.get("name")));
                            Number parent=(Number)cursor.get("parentId");
                            if (parent == null) break;
                            cursor=folderRows.get(parent.longValue());
                            if (cursor == null) { valid=false; break; }
                        }
                        java.util.Collections.reverse(path); shown.put("folderName",valid ? String.join(" / ",path) : "路径不可用");
                    }
                }
                if (shown.isEmpty()) shown.put("kind","wiki_unavailable_result");
                shown.put("turnId",state.path("turnId").asText()); result.add(shown);
            }
            return assembleDisplay(state, result);

        } catch (Exception e) { throw new IllegalStateException("Invalid stored Agent interaction",e); }
    }

    /** Project all interactions of one history response with shared, bounded SQL batches. */
    public List<Map<String,Object>> currentDisplays(User owner, List<String> inputs, List<Boolean> consumed) {
        try {
            ObjectNode merged=JSON.createObjectNode(); merged.put("kind","wiki_write_results"); merged.put("turnId","");
            ArrayNode docs=merged.putArray("documents"), folders=merged.putArray("folders");
            List<JsonNode> states=new ArrayList<>(); List<Integer> docCounts=new ArrayList<>(), folderCounts=new ArrayList<>();
            for (String input : inputs) {
                JsonNode state=JSON.readTree(input); states.add(state); int d=0, f=0;
                if (state.path("documents").isArray()) for(JsonNode item:state.path("documents")){docs.add(item); d++;}
                else if ("wiki_write_result".equals(state.path("kind").asText())) {docs.add(state); d++;}
                if (state.path("folders").isArray()) for(JsonNode item:state.path("folders")){folders.add(item); f++;}
                else if ("wiki_partial_result".equals(state.path("kind").asText())) {folders.add(state); f++;}
                docCounts.add(d); folderCounts.add(f);
            }
            Map<String,Object> all=currentDisplay(owner,merged.toString(),false);
            List<?> items=(List<?>)all.get("results"); int docOffset=0, folderOffset=docs.size();
            List<Map<String,Object>> out=new ArrayList<>();
            for(int i=0;i<states.size();i++) {
                JsonNode state=states.get(i); String kind=state.path("kind").asText();
                if ("destination_choice".equals(kind)) {out.add(safeDisplay(inputs.get(i),consumed.get(i))); continue;}
                List<Map<String,Object>> own=new ArrayList<>();
                for(int j=0;j<docCounts.get(i);j++) { Map<String,Object> item=new LinkedHashMap<>((Map<String,Object>)items.get(docOffset++)); item.put("turnId",state.path("turnId").asText()); own.add(item); }
                for(int j=0;j<folderCounts.get(i);j++) { Map<String,Object> item=new LinkedHashMap<>((Map<String,Object>)items.get(folderOffset++)); item.put("turnId",state.path("turnId").asText()); own.add(item); }
                Map<String,Object> result=assembleDisplay(state, own);
                out.add(result);
            }
            return out;
        } catch (Exception e) { throw new IllegalStateException("Invalid stored Agent interaction",e); }
    }

    /** Match each stored operation with its own authorized SQL result, preserving call order. */
    private Map<String,Object> assembleDisplay(JsonNode state, List<Map<String,Object>> resolved) {
        String kind=state.path("kind").asText();
        List<JsonNode> documents=new ArrayList<>(), folders=new ArrayList<>();
        if (state.path("documents").isArray()) state.path("documents").forEach(documents::add);
        else if ("wiki_write_result".equals(kind)) documents.add(state);
        if (state.path("folders").isArray()) state.path("folders").forEach(folders::add);
        else if ("wiki_partial_result".equals(kind)) folders.add(state);
        List<Map<String,Object>> ordered=new ArrayList<>();
        if (state.path("operations").isArray()) {
            int d=0, f=0;
            for (JsonNode op : state.path("operations")) {
                String tool=op.path("tool").asText(), status=op.path("status").asText();
                if (!java.util.Set.of("create_wiki_folder","create_wiki_document","write_wiki_document").contains(tool)) continue;
                if ("success".equals(status)) {
                    int index="create_wiki_folder".equals(tool) ? documents.size()+f++ : d++;
                    if (index < resolved.size()) ordered.add(resolved.get(index));
                } else {
                    Map<String,Object> item=new LinkedHashMap<>(); item.put("kind","wiki_operation_result");
                    item.put("operation",tool); item.put("status",java.util.Set.of("failed","blocked","unknown").contains(status) ? status : "unavailable");
                    ordered.add(item);
                }
            }
        } else ordered.addAll(resolved);
        Map<String,Object> display=new LinkedHashMap<>();
        if ("wiki_write_result".equals(kind) && !resolved.isEmpty() && "wiki_write_result".equals(resolved.get(0).get("kind")) && resolved.get(0).containsKey("openToken")) {
            display.putAll(resolved.get(0)); display.remove("validPath");
            if (state.path("operations").isArray() && ordered.size()>1) display.put("results",ordered);
            else if (!state.path("operations").isArray() && resolved.size()>1) display.put("results",resolved);
            return display;
        }
        if ("wiki_partial_result".equals(kind) && !resolved.isEmpty() && "wiki_folder_result".equals(resolved.get(0).get("kind"))) {
            display.putAll(resolved.get(0)); display.put("kind","wiki_partial_result"); return display;
        }
        display.put("kind","wiki_write_results"); display.put("turnId",state.path("turnId").asText());
        display.put("results",ordered); display.put("unknown",state.path("unknown").asInt()); display.put("failed",state.path("failed").asInt());
        return display;
    }

    public static String answerFor(Map<String,Object> display, int unknown, int failed) {
        StringBuilder answer = new StringBuilder();
        Object raw = display.get("results");
        if (raw == null && ("wiki_write_result".equals(display.get("kind")) || "wiki_partial_result".equals(display.get("kind")))) {
            raw = java.util.Collections.singletonList(display);
        }
        if (raw instanceof List<?>) for (Object item : (List<?>)raw) {
            Map<?,?> row=(Map<?,?>)item; String kind=String.valueOf(row.get("kind"));
            if ("wiki_write_result".equals(kind)) answer.append("已创建文档「").append(row.get("documentTitle")).append("」，当前位置：").append(row.get("spaceLabel")).append(" / ").append(row.get("folderName")).append("。");
            else if ("wiki_folder_result".equals(kind)) answer.append("已创建文件夹：").append(row.get("spaceLabel")).append(" / ").append(row.get("folderName")).append("。");
            else if ("wiki_partial_result".equals(kind)) answer.append("已在").append(row.get("spaceLabel")).append("创建文件夹「").append(row.get("folderName")).append("」，文档尚未保存。");
            else if ("wiki_operation_result".equals(kind)) {
                String op=String.valueOf(row.get("operation")), status=String.valueOf(row.get("status"));
                String label="create_wiki_folder".equals(op) ? "创建文件夹" : "create_wiki_document".equals(op) ? "创建文档" : "创建文件夹和文档";
                if ("unknown".equals(status)) answer.append(answer.length()>0 ? "另一次写入结果未知（" : "写入结果未知（").append(label).append("），请先核查目标位置。");
                else if ("blocked".equals(status)) answer.append(label).append("操作被阻止。");
                else if ("failed".equals(status)) answer.append(label).append("操作失败。");
                else answer.append(label).append("结果当前不可用。");
            } else answer.append("一项已确认的结果当前不可用。");
        }
        boolean hasOperationDetails = raw instanceof List<?> && ((List<?>) raw).stream().anyMatch(item -> item instanceof Map<?,?> && "wiki_operation_result".equals(((Map<?,?>)item).get("kind")));
        if (!hasOperationDetails) for (int i=0;i<unknown;i++) answer.append(answer.length()>0 ? "另一次写入结果未知，请先核查目标位置。" : "写入结果未知，请先核查目标位置。");
        if (!hasOperationDetails && !"wiki_partial_result".equals(display.get("kind"))) for (int i=0;i<failed;i++) answer.append("另有写入未成功。");
        if (answer.length()==0) answer.append("文档未保存，请检查请求后重试。");
        return answer.toString();
    }

    public static Map<String, Object> safeDisplay(String internalJson, boolean consumed) {
        try {
            JsonNode state = JSON.readTree(internalJson);
            Map<String, Object> display = new LinkedHashMap<>();
            display.put("kind", state.path("kind").asText());
            display.put("turnId", state.path("turnId").asText());
            if ("wiki_write_result".equals(state.path("kind").asText())) {
                display.put("spaceLabel", state.path("spaceLabel").asText());
                display.put("folderName", state.path("folderName").asText(""));
                display.put("documentTitle", state.path("documentTitle").asText());
                display.put("openToken", state.path("openToken").asText());
                return display;
            }
            if ("wiki_partial_result".equals(state.path("kind").asText())) {
                display.put("spaceLabel", state.path("spaceLabel").asText());
                display.put("folderName", state.path("folderName").asText());
                return display;
            }
            display.put("expiresAt", state.path("expiresAt").asLong());
            display.put("consumed", consumed);
            List<Map<String, Object>> options = new ArrayList<>();
            for (JsonNode option : state.path("options")) {
                Map<String, Object> shown = new LinkedHashMap<>();
                shown.put("token", option.path("token").asText());
                shown.put("label", option.path("label").asText());
                shown.put("recommended", option.path("recommended").asBoolean());
                options.add(shown);
            }
            display.put("options", options);
            return display;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid stored Agent interaction", e);
        }
    }

    public Map<String, String> preview(User owner, long conversationId, long turnId, String openToken) {
        if (owner == null || owner.getId() == null || openToken == null ||
                !openToken.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Invalid document action");
        List<String> states = jdbc.query("SELECT m.interactionJson FROM agent_message m " +
                        "JOIN agent_conversation c ON c.id=m.conversationId " +
                        "WHERE m.conversationId=? AND m.turnId=? AND m.role='assistant' " +
                        "AND m.status='DONE' AND c.ownerUserId=? AND c.isDelete=0 AND m.interactionJson IS NOT NULL",
                (rs, row) -> rs.getString(1), conversationId, turnId, owner.getId());
        if (states.size() != 1) throw new IllegalArgumentException("Document action not found");
        final JsonNode state;
        try { state = JSON.readTree(states.get(0)); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid document action", e); }
        if (!owner.getId().toString().equals(state.path("ownerId").asText()) ||
                !Long.toString(conversationId).equals(state.path("conversationId").asText()) ||
                !Long.toString(turnId).equals(state.path("turnId").asText()))
            throw new IllegalArgumentException("Invalid document action");
        JsonNode selected = null;
        List<JsonNode> candidates = new ArrayList<>();
        if ("wiki_write_result".equals(state.path("kind").asText())) candidates.add(state);
        else if ("wiki_write_results".equals(state.path("kind").asText())) state.path("documents").forEach(candidates::add);
        for (JsonNode candidate : candidates) {
            if (MessageDigest.isEqual(openToken.getBytes(StandardCharsets.UTF_8),
                    candidate.path("openToken").asText("").getBytes(StandardCharsets.UTF_8))) { selected = candidate; break; }
        }
        if (selected == null) throw new IllegalArgumentException("Invalid document action");
        String id = selected.path("documentId").asText();
        Map<String,String> location = resolveDocuments(owner, java.util.Collections.singletonList(id)).get(id);
        if (location == null || !"true".equals(location.get("validPath"))) throw new IllegalArgumentException("Document is unavailable");
        long documentId = Long.parseLong(id);
        DocumentWiki document = documents.getById(documentId);
        if (document == null || document.getSpaceId() == null || !Integer.valueOf(0).equals(document.getIsDelete()))
            throw new IllegalArgumentException("Document is unavailable");
        spaces.requireSpaceAccess(document.getSpaceId(), owner, false);
        Map<String, String> shown = new LinkedHashMap<>();
        shown.put("title", document.getTitle());
        shown.put("content", document.getContent());
        shown.put("contentFormat", document.getContentFormat());
        return shown;
    }
}

package com.et.cloud.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

/** Collects signed file-operation evidence for one turn and resolves stored references. */
@Service
public final class AgentPersonalFileReferences {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String PRIVATE_FIELD = "personalFileReferencesInternal";
    private final AgentPersonalFileStore files;
    private final AgentConversationStore conversations;
    private final String secret;
    public AgentPersonalFileReferences(AgentPersonalFileStore files, AgentConversationStore conversations,
                                       @Value("${agent.internal-key:}") String secret) {
        this.files = files; this.conversations = conversations; this.secret = secret;
    }
    public Collector collect(long owner) { return new Collector(owner); }
    public final class Collector {
        private final long owner;
        private final ArrayNode references = JSON.createArrayNode();
        private Collector(long owner) { this.owner = owner; }
        public void accept(JsonNode evidence) {
            if (!evidence.isArray()) return;
            Map<String,String> checkedVersions = new HashMap<>();
            for (JsonNode item : evidence) {
                if (!item.isTextual() || references.size() >= 1000) continue;
                try {
                    JsonNode value = AgentPersonalFileEvidence.verify(secret, item.textValue(), owner);
                    String path = value.path("path").asText(), version = value.path("version").asText();
                    String currentVersion = checkedVersions.computeIfAbsent(path, key -> files.version(owner, key));
                    if (!version.equals(currentVersion)) continue;
                    ObjectNode reference = references.addObject();
                    reference.put("token", UUID.randomUUID().toString());
                    reference.put("path", path); reference.put("version", version);
                    reference.put("start", value.path("start").longValue());
                    reference.put("end", value.path("end").longValue());
                } catch (IllegalArgumentException | IllegalStateException invalid) { /* Ignore invalid or stale evidence. */ }
            }
        }
        public String stats(String raw) {
            try {
                JsonNode parsed = raw == null ? null : JSON.readTree(raw);
                ObjectNode stats = parsed != null && parsed.isObject() ? (ObjectNode) parsed : JSON.createObjectNode();
                stats.remove(PRIVATE_FIELD);
                if (!references.isEmpty()) stats.set(PRIVATE_FIELD, references);
                return JSON.writeValueAsString(stats);
            } catch (Exception invalid) { throw new IllegalStateException("Invalid Agent statistics"); }
        }
    }
    public Map<String,Object> resolve(long owner, long conversation, long message, String token) {
        conversations.requireOwner(owner, conversation);
        if (token == null || !token.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid personal file reference");
        Map<String,Object> row = conversations.messagesRaw(owner, conversation).stream()
                .filter(r -> Long.toString(message).equals(String.valueOf(r.get("id"))) && "assistant".equals(r.get("role")) && "DONE".equals(r.get("status")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid personal file reference"));
        try {
            JsonNode stats = JSON.readTree(String.valueOf(row.get("statsJson")));
            for (JsonNode ref : stats.path(PRIVATE_FIELD)) {
                if (!token.equals(ref.path("token").asText())) continue;
                String path = ref.path("path").asText(), version = ref.path("version").asText();
                if (!version.equals(files.version(owner, path))) throw new IllegalArgumentException("Personal file reference expired");
                Map<String,Object> result = new LinkedHashMap<>();
                result.put("path", path); result.put("version", version);
                result.put("start", ref.path("start").longValue()); result.put("end", ref.path("end").longValue());
                return result;
            }
        } catch (java.io.IOException | IllegalStateException invalid) {
            throw new IllegalArgumentException("Personal file reference expired");
        }
        throw new IllegalArgumentException("Invalid personal file reference");
    }
    public List<Map<String,Object>> previewInfo(long owner, long conversation, long message) {
        Map<String,Object> row = conversations.messagesRaw(owner, conversation).stream()
                .filter(r -> Long.toString(message).equals(String.valueOf(r.get("id"))) && "assistant".equals(r.get("role")) && "DONE".equals(r.get("status")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid personal file reference"));
        List<Map<String,Object>> result = new ArrayList<>();
        try {
            JsonNode stats = JSON.readTree(String.valueOf(row.get("statsJson")));
            for (JsonNode ref : stats.path(PRIVATE_FIELD)) {
                String path = ref.path("path").asText();
                if (path.isEmpty() || !ref.path("token").isTextual()) continue;
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("token", ref.path("token").asText());
                item.put("name", path.substring(path.lastIndexOf('/') + 1));
                item.put("start", ref.path("start").longValue()); item.put("end", ref.path("end").longValue());
                result.add(item);
            }
            return result;
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid personal file reference"); }
    }
    private static final int PREVIEW_BYTES = 16384;
    public Map<String,Object> preview(long owner, long conversation, long message, String token, String cursor) {
        Map<String,Object> ref = resolve(owner, conversation, message, token);
        String path = (String) ref.get("path"), version = (String) ref.get("version");
        long start; int limit;
        if (cursor == null || cursor.isEmpty()) {
            start = (long) ref.get("start"); limit = PREVIEW_BYTES;
        } else {
            long[] position = decodePreviewCursor(owner, conversation, message, token, cursor);
            start = position[0]; limit = (int) position[1];
            if (start < 0 || limit < 1 || limit > PREVIEW_BYTES + 3) throw new IllegalArgumentException("Invalid preview cursor");
        }
        AgentPersonalFileStore.Segment segment = files.previewSegment(owner, path, version, start, limit);
        long next = start + segment.text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("text", segment.text); result.put("start", start); result.put("referenceStart", ref.get("start"));
        result.put("referenceEnd", ref.get("end"));
        result.put("next", segment.next == null ? null : encodePreviewCursor(owner, conversation, message, token, next, PREVIEW_BYTES));
        if (start > 0) {
            long previous = files.previewBoundary(owner, path, version, Math.max(0, start - PREVIEW_BYTES));
            result.put("previous", encodePreviewCursor(owner, conversation, message, token, previous, (int)(start - previous)));
        } else result.put("previous", null);
        return result;
    }
    private String encodePreviewCursor(long owner, long conversation, long message, String token, long start, int limit) {
        try {
            byte[] nonce = new byte[12]; new java.security.SecureRandom().nextBytes(nonce);
            javax.crypto.Cipher cipher = previewCipher(javax.crypto.Cipher.ENCRYPT_MODE, owner, conversation, message, token, nonce);
            byte[] data = cipher.doFinal(java.nio.ByteBuffer.allocate(12).putLong(start).putInt(limit).array());
            byte[] combined = java.nio.ByteBuffer.allocate(nonce.length + data.length).put(nonce).put(data).array();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Preview unavailable"); }
    }
    private long[] decodePreviewCursor(long owner, long conversation, long message, String token, String cursor) {
        try {
            if (cursor.length() > 128) throw new IllegalArgumentException();
            byte[] combined = Base64.getUrlDecoder().decode(cursor);
            if (combined.length != 40) throw new IllegalArgumentException();
            byte[] nonce = Arrays.copyOfRange(combined, 0, 12);
            byte[] plain = previewCipher(javax.crypto.Cipher.DECRYPT_MODE, owner, conversation, message, token, nonce)
                    .doFinal(Arrays.copyOfRange(combined, 12, combined.length));
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(plain);
            return new long[]{buffer.getLong(), buffer.getInt()};
        } catch (Exception e) { throw new IllegalArgumentException("Invalid preview cursor"); }
    }
    private javax.crypto.Cipher previewCipher(int mode, long owner, long conversation, long message, String token, byte[] nonce)
            throws java.security.GeneralSecurityException {
        byte[] key = java.security.MessageDigest.getInstance("SHA-256").digest(("personal-preview-v1\0" + secret).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new javax.crypto.spec.SecretKeySpec(key, "AES"), new javax.crypto.spec.GCMParameterSpec(128, nonce));
        cipher.updateAAD((owner + ":" + conversation + ":" + message + ":" + token).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return cipher;
    }
    static void project(Map<String,Object> row) {
        Object raw = row.get("statsJson");
        if (raw == null) return;
        try {
            JsonNode parsed = JSON.readTree(String.valueOf(raw));
            if (parsed == null || !parsed.isObject()) return;
            JsonNode references = ((ObjectNode)parsed).remove(PRIVATE_FIELD);
            if (references == null) return;
            List<Map<String,Object>> display = new ArrayList<>();
            if (references.isArray()) for (JsonNode ref : references) {
                if (ref.path("token").isTextual()) display.add(Collections.singletonMap("token", ref.path("token").asText()));
            }
            row.put("personalFileReferences", display);
            row.put("statsJson", JSON.writeValueAsString(parsed));
        } catch (java.io.IOException ignored) { /* Legacy malformed data remains untouched. */ }
    }
}

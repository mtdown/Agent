package com.et.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Request-local Wiki aliases and model-facing identifier protection. */
    /** Request-local aliases keep actual Wiki IDs and cursors outside model history. */
final class AgentWikiBoundary {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    static boolean isWikiTool(String name) {
        return AgentToolRegistry.names().contains(name) && AgentToolRegistry.domain(name) == AgentToolRegistry.Domain.WIKI;
    }
        static boolean isWrite(String name) {
        return isWikiTool(name) && AgentToolRegistry.access(name) == AgentToolRegistry.Access.WRITE;
    }
    static void recordEvent(String name, String result, List<AgentLoop.ToolEvent> events) {
        if (isWikiTool(name)) events.add(new AgentLoop.ToolEvent(name, result));
    }
    static boolean choiceRequired(String name, String result) {
        if (!"plan_wiki_destination".equals(name)) return false;
        try { return JSON.readTree(result).path("choiceRequired").asBoolean(false); }
        catch (java.io.IOException e) { throw new IllegalStateException("Invalid destination planning result", e); }
    }
    String argumentsFor(String name, String arguments) {
        return isWikiTool(name) ? decodeArguments(arguments) : arguments;
    }
    String resultFor(String name, String result) {
        return isWikiTool(name) ? encodeResult(result) : "get_skill".equals(name) ? encodeSkillResult(result) : result;
    }
    private final Map<String,String> toAlias = new HashMap<>(), toId = new HashMap<>();
        private final java.security.SecureRandom random = new java.security.SecureRandom();
        private final Set<String> sensitiveValues = new HashSet<>();
        private static boolean identifierField(String key) {
            String name=key.toLowerCase(Locale.ROOT);
            return Arrays.asList("id", "spaceid", "folderid", "documentid", "userid", "parentid",
                    "defaultpersonalspaceid", "recommendedspaceid", "cursor", "nextcursor").contains(name);
        }
        private String alias(String value) {
            return toAlias.computeIfAbsent(value, id -> {
                byte[] bytes=new byte[24]; String token;
                do {
                    random.nextBytes(bytes); StringBuilder opaque=new StringBuilder("wiki_");
                    for (byte b:bytes) { opaque.append((char)('a'+((b & 0xff) >>> 4))); opaque.append((char)('a'+(b & 15))); }
                    token=opaque.toString();
                } while (toId.containsKey(token));
                toId.put(token,id); return token;
            });
        }
        /** Transform only structured, fixed Wiki tool arguments in the returned Skill copy. */
        String encodeSkillResult(String text) {
            try {
                com.fasterxml.jackson.databind.JsonNode envelope=JSON.readTree(text);
                if (envelope == null || !envelope.isObject()) return text;
                com.fasterxml.jackson.databind.node.ObjectNode object=(com.fasterxml.jackson.databind.node.ObjectNode)envelope;
                boolean changed=false;
                com.fasterxml.jackson.databind.JsonNode definition=object.get("definition");
                if (definition != null && definition.isObject()) changed=encodeSkillDefinition(definition);
                com.fasterxml.jackson.databind.JsonNode encoded=object.get("definitionJson");
                if (encoded != null && encoded.isTextual()) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode parsed=JSON.readTree(encoded.asText());
                        if (parsed != null && parsed.isObject() && encodeSkillDefinition(parsed)) {
                            object.put("definitionJson",JSON.writeValueAsString(parsed)); changed=true;
                        }
                    } catch (java.io.IOException ignored) { /* Not a structured definition. */ }
                }
                return changed ? JSON.writeValueAsString(object) : text;
            } catch (Exception invalid) { return text; }
        }
        private boolean encodeSkillDefinition(com.fasterxml.jackson.databind.JsonNode definition) {
            com.fasterxml.jackson.databind.JsonNode steps=definition.path("steps");
            if (!steps.isArray()) return false;
            boolean changed=false;
            for (com.fasterxml.jackson.databind.JsonNode step:steps) {
                if (!"tool".equals(step.path("type").asText()) || !isWikiTool(step.path("tool").asText())) continue;
                com.fasterxml.jackson.databind.JsonNode args=step.path("args");
                if (!args.isObject()) continue;
                String tool=step.path("tool").asText();
                List<String> fields=new ArrayList<>();
                if (Arrays.asList("write_wiki_document","create_wiki_folder","create_wiki_document","list_writable_wiki_locations").contains(tool)) fields.add("spaceId");
                if ("create_wiki_document".equals(tool)) fields.add("folderId");
                if ("list_writable_wiki_locations".equals(tool)) fields.add("cursor");
                for (String field:fields) {
                    com.fasterxml.jackson.databind.JsonNode value=args.get(field);
                    if (value == null || !value.isTextual() || !value.asText().matches("[1-9][0-9]{0,18}")) continue;
                    try { Long.parseLong(value.asText()); } catch (NumberFormatException invalid) { continue; }
                    ((com.fasterxml.jackson.databind.node.ObjectNode)args).put(field,alias(value.asText())); changed=true;
                }
            }
            return changed;
        }
        String encodeResult(String text) {
            try {
                com.fasterxml.jackson.databind.JsonNode node=JSON.readTree(text);
                if (node == null || !node.isObject()) throw new IllegalArgumentException();
                collectSensitive(node); encodeNode(node); return JSON.writeValueAsString(node);
            } catch (Exception e) { return "{\"saved\":false,\"error\":\"Wiki operation result unavailable\"}"; }
        }
        private void collectSensitive(com.fasterxml.jackson.databind.JsonNode node) {
            if (node.isArray()) { node.forEach(this::collectSensitive); return; }
            if (!node.isObject()) return;
            if ("personal".equals(node.path("type").asText()) && node.path("name").isTextual())
                sensitiveValues.add(node.path("name").asText());
            node.fields().forEachRemaining(field -> {
                com.fasterxml.jackson.databind.JsonNode value=field.getValue();
                if (identifierField(field.getKey()) && value.isValueNode() && !value.isNull() && !value.asText().isEmpty())
                    sensitiveValues.add(value.asText());
                else collectSensitive(value);
            });
        }
        private void encodeNode(com.fasterxml.jackson.databind.JsonNode node) {
            if (node.isArray()) { node.forEach(this::encodeNode); return; }
            if (!node.isObject()) return;
            com.fasterxml.jackson.databind.node.ObjectNode object=(com.fasterxml.jackson.databind.node.ObjectNode)node;
            if ("personal".equals(object.path("type").asText()) && object.has("name")) object.put("name", "个人空间");
            List<String> fields=new ArrayList<>(); object.fieldNames().forEachRemaining(fields::add);
            for(String key:fields) {
                com.fasterxml.jackson.databind.JsonNode value=object.get(key);
                if (identifierField(key) && value.isValueNode() && !value.isNull() && !value.asText().isEmpty())
                    object.put(key,alias(value.asText()));
                else if (value.isTextual()) {
                    String text=value.asText();
                    for (String secret:sensitiveValues) if (!secret.isEmpty()) text=text.replace(secret,"[隐藏]");
                    object.put(key,text);
                } else encodeNode(value);
            }
        }
        String decodeArguments(String text) {
            try {
                com.fasterxml.jackson.databind.JsonNode node=JSON.readTree(text); decodeNode(node);
                return JSON.writeValueAsString(node);
            } catch (IllegalArgumentException e) { throw e; }
            catch (Exception e) { throw new IllegalArgumentException("Invalid Wiki tool arguments"); }
        }
        private void decodeNode(com.fasterxml.jackson.databind.JsonNode node) {
            if (node.isArray()) { node.forEach(this::decodeNode); return; }
            if (!node.isObject()) return;
            com.fasterxml.jackson.databind.node.ObjectNode object=(com.fasterxml.jackson.databind.node.ObjectNode)node;
            List<String> fields=new ArrayList<>(); object.fieldNames().forEachRemaining(fields::add);
            for(String key:fields) {
                com.fasterxml.jackson.databind.JsonNode value=object.get(key);
                if (identifierField(key) && !value.isNull()) {
                    String id=toId.get(value.asText());
                    if (id == null) throw new IllegalArgumentException("Invalid or expired Wiki location reference");
                    object.put(key,id);
                } else decodeNode(value);
            }
        }
}

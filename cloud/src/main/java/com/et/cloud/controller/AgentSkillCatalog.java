package com.et.cloud.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Bounded per-user overview sent to the Agent; full steps remain in Cloud. */
@Component
public final class AgentSkillCatalog {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ITEMS = 20;
    private static final int MAX_CHARS = 8000;
    private final AgentLibraryStore store;

    public AgentSkillCatalog(AgentLibraryStore store) { this.store = store; }

    public String forUser(long user) {
        StringBuilder catalog = new StringBuilder();
        int count = 0;
        for (AgentLibraryStore.SkillItem item : store.listAvailable(user)) {
            if (count++ >= MAX_ITEMS) break;
            try {
                AgentLibraryStore.SkillVersion version = store.skillVersion(user, item.id, item.version);
                JsonNode definition = JSON.readTree(version.definitionJson);
                String line = item.id + "@" + item.version + " | " + clean(item.name, 160) + " | " +
                        clean(item.summary, 500) + " | when_to_use: " + clean(definition.path("when_to_use").asText(), 1000) +
                        " | when_not_to_use: " + clean(definition.path("when_not_to_use").asText(), 1000) + "\n";
                if (catalog.length() + line.length() > MAX_CHARS) break;
                catalog.append(line);
            } catch (Exception ignored) {
                // A damaged version is omitted from selection; it cannot be safely executed.
            }
        }
        return catalog.toString();
    }

    private static String clean(String raw, int max) {
        if (raw == null) return "";
        String oneLine = raw.replaceAll("[\\p{Cntrl}]+", " ").trim();
        return oneLine.substring(0, Math.min(max, oneLine.length()));
    }
}

package com.et.cloud.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Versioned, platform-owned workflow; its reserved ID cannot be a user record ID. */
final class AgentBuiltInWikiAnswerSkill {
    static final String ID = "builtin:wiki-answer";
    static final int VERSION = 1;
    static final String DEFINITION = loadDefinition();

    private AgentBuiltInWikiAnswerSkill() { }

    private static String loadDefinition() {
        try (InputStream source = AgentBuiltInWikiAnswerSkill.class.getResourceAsStream("/agent-wiki-answer-skill.json")) {
            if (source == null) throw new IllegalStateException("Built-in Wiki Skill resource is missing");
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read built-in Wiki Skill", e);
        }
    }

    static AgentLibraryStore.SkillItem item() {
        AgentLibraryStore.SkillItem item = new AgentLibraryStore.SkillItem();
        item.id = ID;
        item.name = "Wiki 内容问答";
        item.summary = "基于本轮已有的 Wiki 检索证据回答并引用来源";
        item.state = "ACTIVE";
        item.version = VERSION;
        item.latestVersion = VERSION;
        item.owner = false;
        return item;
    }

    static AgentLibraryStore.SkillVersion version(int requestedVersion) {
        if (requestedVersion != VERSION) throw new IllegalArgumentException("Built-in Skill version not found");
        AgentLibraryStore.SkillVersion version = new AgentLibraryStore.SkillVersion();
        version.id = ID;
        version.version = VERSION;
        version.definitionJson = DEFINITION;
        return version;
    }
}

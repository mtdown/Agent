package com.et.cloud.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Versioned, platform-owned workflow; its reserved ID cannot be a user record ID. */
final class AgentBuiltInWikiWriteSkill {
    static final String ID = "builtin:wiki-write";
    static final int VERSION = 1;
    static final String DEFINITION = loadDefinition();

    private AgentBuiltInWikiWriteSkill() { }

    private static String loadDefinition() {
        try (InputStream source = AgentBuiltInWikiWriteSkill.class.getResourceAsStream("/agent-wiki-write-skill.json")) {
            if (source == null) throw new IllegalStateException("Built-in Wiki Skill resource is missing");
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read built-in Wiki Skill", e);
        }
    }

    static AgentLibraryStore.SkillItem item() {
        AgentLibraryStore.SkillItem item = new AgentLibraryStore.SkillItem();
        item.id = ID;
        item.name = "文档写入流程";
        item.summary = "将用户提供的内容写入有权限的 Wiki 空间";
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

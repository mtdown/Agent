package com.et.cloud.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

/** Owner-scoped persistence for immutable Skill and code versions. */
@Service
public class AgentLibraryStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AgentSkillDefinitionValidator skillValidator;
    private final AgentCodeVersionValidator codeValidator;

    public AgentLibraryStore(DataSource dataSource, PlatformTransactionManager manager,
                             AgentSkillDefinitionValidator skillValidator, AgentCodeVersionValidator codeValidator) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(manager);
        this.skillValidator = skillValidator;
        this.codeValidator = codeValidator;
    }

    public static final class SkillItem {
        public String id;
        public String name;
        public String summary;
        public String state;
        public int version;
        public int latestVersion;
        public boolean owner;
    }

    public static final class CodeItem {
        public String id;
        public String name;
        public String summary;
        public int latestVersion;
        public boolean owner;
    }

    public static final class SkillVersion {
        public String id;
        public int version;
        public String definitionJson;
    }

    public static final class CodeVersion {
        public String id;
        public int version;
        public String language;
        public String entrypoint;
        public String sourceCode;
        public String inputSchemaJson;
        public String outputSchemaJson;
        public String validationState;
    }

    public String createCode(long owner, String name, String summary, String language, String entrypoint,
                             String source, String inputSchema, String outputSchema) {
        String title = label(name, 160);
        String description = label(summary, 500);
        codeValidator.validate(language, entrypoint, source, inputSchema, outputSchema);
        return transactions.execute(status -> {
            long id = IdWorker.getId();
            jdbc.update("INSERT INTO agent_code_item (id, ownerUserId, name, summary, latestVersion) VALUES (?, ?, ?, ?, 1)",
                    id, owner, title, description);
            insertCodeVersion(id, 1, owner, language, entrypoint, source, inputSchema, outputSchema);
            return Long.toString(id);
        });
    }

    public int publishCode(long owner, String rawId, String language, String entrypoint,
                           String source, String inputSchema, String outputSchema) {
        codeValidator.validate(language, entrypoint, source, inputSchema, outputSchema);
        long id = id(rawId);
        return transactions.execute(status -> {
            int next = lockedLatest("agent_code_item", owner, id) + 1;
            insertCodeVersion(id, next, owner, language, entrypoint, source, inputSchema, outputSchema);
            jdbc.update("UPDATE agent_code_item SET latestVersion = ? WHERE id = ? AND ownerUserId = ? AND isDelete = 0",
                    next, id, owner);
            return next;
        });
    }

    private void insertCodeVersion(long id, int version, long owner, String language, String entrypoint,
                                   String source, String inputSchema, String outputSchema) {
        jdbc.update("INSERT INTO agent_code_version (codeId, version, language, entrypoint, sourceCode, " +
                        "inputSchemaJson, outputSchemaJson, validationState, createdBy) VALUES (?, ?, ?, ?, ?, ?, ?, 'UNVERIFIED', ?)",
                id, version, language, entrypoint, source, inputSchema, outputSchema, owner);
    }

    public CodeVersion codeVersion(long user, String rawId, int version) {
        long id = id(rawId);
        requireCodeAccess(user, id);
        List<CodeVersion> versions = jdbc.query("SELECT version, language, entrypoint, sourceCode, " +
                        "inputSchemaJson, outputSchemaJson, validationState FROM agent_code_version " +
                        "WHERE codeId = ? AND version = ?",
                (rs, row) -> {
                    CodeVersion result = new CodeVersion();
                    result.id = rawId;
                    result.version = rs.getInt("version");
                    result.language = rs.getString("language");
                    result.entrypoint = rs.getString("entrypoint");
                    result.sourceCode = rs.getString("sourceCode");
                    result.inputSchemaJson = rs.getString("inputSchemaJson");
                    result.outputSchemaJson = rs.getString("outputSchemaJson");
                    result.validationState = rs.getString("validationState");
                    return result;
                }, id, version);
        if (versions.isEmpty()) throw new IllegalArgumentException("Code version not found");
        return versions.get(0);
    }

    /** Called by the platform validator after it has executed the declared samples. */
    public void markCodeValidated(String rawId, int version, boolean passed) {
        int updated = jdbc.update("UPDATE agent_code_version SET validationState = ? WHERE codeId = ? AND version = ?",
                passed ? "PASSED" : "FAILED", id(rawId), version);
        if (updated != 1) throw new IllegalArgumentException("Code version not found");
    }

    public void shareCode(long owner, String rawId, long target) {
        long id = id(rawId);
        requireCodeOwner(owner, id);
        if (owner == target) throw new IllegalArgumentException("Cannot share with owner");
        jdbc.update("INSERT IGNORE INTO agent_code_share (codeId, targetUserId) VALUES (?, ?)", id, target);
    }

    public String createSkill(long owner, String definitionJson) {
        JsonNode definition = validateSkill(owner, definitionJson);
        return transactions.execute(status -> {
            long id = IdWorker.getId();
            jdbc.update("INSERT INTO agent_skill_item (id, ownerUserId, name, summary, state, activeVersion, latestVersion) " +
                            "VALUES (?, ?, ?, ?, 'ACTIVE', 1, 1)",
                    id, owner, definition.get("name").asText(), definition.get("summary").asText());
            jdbc.update("INSERT INTO agent_skill_version (skillId, version, definitionJson, createdBy) " +
                    "VALUES (?, 1, ?, ?)", id, definitionJson, owner);
            return Long.toString(id);
        });
    }

    public int reviseSkill(long owner, String rawId, String definitionJson) {
        JsonNode definition = validateSkill(owner, definitionJson);
        long id = id(rawId);
        return transactions.execute(status -> {
            int next = lockedLatest("agent_skill_item", owner, id) + 1;
            jdbc.update("INSERT INTO agent_skill_version (skillId, version, definitionJson, createdBy) " +
                    "VALUES (?, ?, ?, ?)", id, next, definitionJson, owner);
            jdbc.update("UPDATE agent_skill_item SET name = ?, summary = ?, latestVersion = ?, " +
                            "activeVersion = ?, state = 'ACTIVE' " +
                            "WHERE id = ? AND ownerUserId = ? AND isDelete = 0",
                    definition.get("name").asText(), definition.get("summary").asText(), next, next, id, owner);
            return next;
        });
    }

    public void enableSkill(long owner, String rawId, int version) {
        long id = id(rawId);
        requireSkillOwner(owner, id);
        SkillVersion chosen = skillVersion(owner, rawId, version);
        validateSkill(owner, chosen.definitionJson);
        int changed = jdbc.update("UPDATE agent_skill_item SET state = 'ACTIVE', activeVersion = ? " +
                        "WHERE id = ? AND ownerUserId = ? AND isDelete = 0",
                version, id, owner);
        if (changed != 1) throw new IllegalArgumentException("Skill not found");
    }

    public void disableSkill(long owner, String rawId) {
        long id = id(rawId);
        int changed = jdbc.update("UPDATE agent_skill_item SET state = 'DISABLED' " +
                "WHERE id = ? AND ownerUserId = ? AND isDelete = 0", id, owner);
        if (changed != 1) throw new IllegalArgumentException("Skill not found");
    }

    public SkillVersion skillVersion(long user, String rawId, int version) {
        if (AgentBuiltInWikiWriteSkill.ID.equals(rawId)) return AgentBuiltInWikiWriteSkill.version(version);
        long id = id(rawId);
        requireSkillAccess(user, id);
        List<SkillVersion> versions = jdbc.query("SELECT version, definitionJson FROM agent_skill_version " +
                        "WHERE skillId = ? AND version = ?", (rs, row) -> {
                    SkillVersion result = new SkillVersion();
                    result.id = rawId;
                    result.version = rs.getInt("version");
                    result.definitionJson = rs.getString("definitionJson");
                    return result;
                }, id, version);
        if (versions.isEmpty()) throw new IllegalArgumentException("Skill version not found");
        return versions.get(0);
    }

    public SkillVersion enabledSkillVersion(long user, String rawId, int version) {
        if (AgentBuiltInWikiWriteSkill.ID.equals(rawId)) return AgentBuiltInWikiWriteSkill.version(version);
        long id = id(rawId);
        List<SkillVersion> versions = jdbc.query("SELECT v.version, v.definitionJson FROM agent_skill_item s " +
                        "JOIN agent_skill_version v ON v.skillId = s.id AND v.version = s.activeVersion " +
                        "WHERE s.id = ? AND s.isDelete = 0 AND s.state = 'ACTIVE' AND s.activeVersion = ? " +
                        "AND (s.ownerUserId = ? OR EXISTS (SELECT 1 FROM agent_skill_share x " +
                        "WHERE x.skillId = s.id AND x.targetUserId = ?))",
                (rs, row) -> {
                    SkillVersion result = new SkillVersion();
                    result.id = rawId;
                    result.version = rs.getInt("version");
                    result.definitionJson = rs.getString("definitionJson");
                    return result;
                }, id, version, user, user);
        if (versions.isEmpty()) throw new IllegalArgumentException("Enabled Skill version not found");
        return versions.get(0);
    }

    public void shareSkill(long owner, String rawId, long target) {
        long id = id(rawId);
        requireSkillOwner(owner, id);
        if (owner == target) throw new IllegalArgumentException("Cannot share with owner");
        jdbc.update("INSERT IGNORE INTO agent_skill_share (skillId, targetUserId) VALUES (?, ?)", id, target);
    }

    public List<SkillItem> listAvailable(long user) {
        List<SkillItem> available = jdbc.query("SELECT CAST(s.id AS CHAR) id, s.name, s.summary, s.state, s.activeVersion " +
                        "FROM agent_skill_item s WHERE s.isDelete = 0 AND s.state = 'ACTIVE' " +
                        "AND (s.ownerUserId = ? OR EXISTS (SELECT 1 FROM agent_skill_share x " +
                        "WHERE x.skillId = s.id AND x.targetUserId = ?)) " +
                        "ORDER BY s.updateTime DESC, s.id DESC LIMIT 100",
                (rs, row) -> {
                    SkillItem result = new SkillItem();
                    result.id = rs.getString("id");
                    result.name = rs.getString("name");
                    result.summary = rs.getString("summary");
                    result.state = rs.getString("state");
                    result.version = rs.getInt("activeVersion");
                    return result;
                }, user, user);
        available.add(0, AgentBuiltInWikiWriteSkill.item());
        return available;
    }

    public List<SkillItem> listSkills(long user) {
        List<SkillItem> available = jdbc.query("SELECT CAST(s.id AS CHAR) id, s.name, s.summary, s.state, " +
                        "s.activeVersion, s.latestVersion, s.ownerUserId FROM agent_skill_item s " +
                        "WHERE s.isDelete = 0 AND (s.ownerUserId = ? OR (s.state = 'ACTIVE' AND EXISTS " +
                        "(SELECT 1 FROM agent_skill_share x WHERE x.skillId = s.id AND x.targetUserId = ?))) " +
                        "ORDER BY s.updateTime DESC, s.id DESC LIMIT 100",
                (rs, row) -> {
                    SkillItem result = new SkillItem();
                    result.id = rs.getString("id");
                    result.name = rs.getString("name");
                    result.summary = rs.getString("summary");
                    result.state = rs.getString("state");
                    result.version = rs.getInt("activeVersion");
                    result.latestVersion = rs.getInt("latestVersion");
                    result.owner = rs.getLong("ownerUserId") == user;
                    return result;
                }, user, user);
        available.add(0, AgentBuiltInWikiWriteSkill.item());
        return available;
    }

    public List<CodeItem> listCodes(long user) {
        return jdbc.query("SELECT CAST(c.id AS CHAR) id, c.name, c.summary, c.latestVersion, c.ownerUserId " +
                        "FROM agent_code_item c WHERE c.isDelete = 0 AND (c.ownerUserId = ? OR EXISTS " +
                        "(SELECT 1 FROM agent_code_share x WHERE x.codeId = c.id AND x.targetUserId = ?)) " +
                        "ORDER BY c.updateTime DESC, c.id DESC LIMIT 100",
                (rs, row) -> {
                    CodeItem result = new CodeItem();
                    result.id = rs.getString("id");
                    result.name = rs.getString("name");
                    result.summary = rs.getString("summary");
                    result.latestVersion = rs.getInt("latestVersion");
                    result.owner = rs.getLong("ownerUserId") == user;
                    return result;
                }, user, user);
    }

    private JsonNode validateSkill(long user, String definitionJson) {
        return skillValidator.validate(definitionJson, (codeId, version, entrypoint) ->
                jdbc.queryForObject("SELECT COUNT(*) FROM agent_code_item c " +
                                "JOIN agent_code_version v ON v.codeId = c.id " +
                                "WHERE c.id = ? AND c.isDelete = 0 AND v.version = ? " +
                                "AND v.entrypoint = ? " +
                                "AND (c.ownerUserId = ? OR EXISTS (SELECT 1 FROM agent_code_share x " +
                                "WHERE x.codeId = c.id AND x.targetUserId = ?))",
                        Integer.class, Long.parseLong(codeId), version, entrypoint, user, user) > 0);
    }

    private int lockedLatest(String table, long owner, long id) {
        List<Integer> matches = jdbc.query("SELECT latestVersion FROM " + table +
                        " WHERE id = ? AND ownerUserId = ? AND isDelete = 0 FOR UPDATE",
                (rs, row) -> rs.getInt(1), id, owner);
        if (matches.isEmpty()) throw new IllegalArgumentException("Item not found or not owned");
        return matches.get(0);
    }

    private void requireSkillAccess(long user, long id) {
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM agent_skill_item s WHERE s.id = ? AND s.isDelete = 0 " +
                        "AND (s.ownerUserId = ? OR EXISTS (SELECT 1 FROM agent_skill_share x " +
                        "WHERE x.skillId = s.id AND x.targetUserId = ?))", Integer.class, id, user, user);
        if (count == 0) throw new IllegalArgumentException("Skill not found");
    }

    private void requireCodeAccess(long user, long id) {
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM agent_code_item c WHERE c.id = ? AND c.isDelete = 0 " +
                        "AND (c.ownerUserId = ? OR EXISTS (SELECT 1 FROM agent_code_share x " +
                        "WHERE x.codeId = c.id AND x.targetUserId = ?))", Integer.class, id, user, user);
        if (count == 0) throw new IllegalArgumentException("Code not found");
    }

    private void requireSkillOwner(long owner, long id) {
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM agent_skill_item " +
                "WHERE id = ? AND ownerUserId = ? AND isDelete = 0", Integer.class, id, owner);
        if (count == 0) throw new IllegalArgumentException("Skill not found or not owned");
    }

    private void requireCodeOwner(long owner, long id) {
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM agent_code_item " +
                "WHERE id = ? AND ownerUserId = ? AND isDelete = 0", Integer.class, id, owner);
        if (count == 0) throw new IllegalArgumentException("Code not found or not owned");
    }

    private static long id(String raw) {
        if (raw == null || !raw.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid ID");
        try { return Long.parseLong(raw); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid ID"); }
    }

    private static String label(String raw, int max) {
        if (raw == null || raw.trim().isEmpty() || raw.trim().length() > max)
            throw new IllegalArgumentException("Invalid label");
        return raw.trim();
    }
}

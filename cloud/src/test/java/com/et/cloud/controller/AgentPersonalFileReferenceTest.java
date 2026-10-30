package com.et.cloud.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AgentPersonalFileReferenceTest {
    @TempDir Path root;

    @Test void successfulSegmentProvidesAuthenticatedReferenceEvidence() {
        AgentPersonalFileStore files = new AgentPersonalFileStore(root);
        files.write(7, "notes.txt", "你好 world", "create");
        var controller = new AgentResourceInternalController(files, "secret");
        var data = controller.readSegment("secret", "7", "notes.txt", null).getData();
        assertNotNull(data.get("referenceEvidence"), "Only successful file results may produce reference evidence");
        String token = (String) ((java.util.List<?>) data.get("referenceEvidence")).get(0);
        var evidence = AgentPersonalFileEvidence.verify("secret", token, 7);
        assertEquals(0, evidence.path("start").asLong());
        assertEquals("你好 world".getBytes(java.nio.charset.StandardCharsets.UTF_8).length, evidence.path("end").asLong());
        assertThrows(IllegalArgumentException.class, () -> AgentPersonalFileEvidence.verify("secret", token, 8));
        assertThrows(IllegalArgumentException.class, () -> AgentPersonalFileEvidence.verify("secret", token + "x", 7));
        String[] parts = token.split("\\.");
        String altered = new String(java.util.Base64.getUrlDecoder().decode(parts[0]), java.nio.charset.StandardCharsets.UTF_8).replace("\"start\":0", "\"start\":1");
        String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(altered.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[1];
        assertThrows(IllegalArgumentException.class, () -> AgentPersonalFileEvidence.verify("secret", forged, 7));
    }

    @Test void searchProvidesEvidenceForEachRealMatch() {
        AgentPersonalFileStore files = new AgentPersonalFileStore(root);
        files.write(7, "notes.txt", "你好 你好", "create");
        var data = new AgentResourceInternalController(files, "secret").searchFile("secret", "7", "notes.txt", "你好", null).getData();
        var tokens = (java.util.List<?>) data.get("referenceEvidence");
        assertNotNull(tokens);
        assertEquals(2, tokens.size());
        assertEquals(7, AgentPersonalFileEvidence.verify("secret", (String) tokens.get(1), 7).path("start").asLong());
    }

    @Test void legacyReadProvidesEvidenceForActualWholeFile() {
        AgentPersonalFileStore files = new AgentPersonalFileStore(root);
        files.write(7, "notes.txt", "你好", "create");
        var data = new AgentResourceInternalController(files, "secret").readFile("secret", "7", "notes.txt").getData();
        assertNotNull(data.get("referenceEvidence"));
    }
}

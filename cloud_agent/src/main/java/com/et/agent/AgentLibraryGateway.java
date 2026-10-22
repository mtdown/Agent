package com.et.agent;

/** Cloud-backed library operations performed for the authenticated chat user. */
public interface AgentLibraryGateway {
    String getSkill(String userId, String id, int version);
    String createSkill(String userId, String definition);
    String createCode(String userId, String definition);
    String writeFile(String userId, String path, String content, String mode);
    String readFile(String userId, String path);
    String writeWikiDocument(String userId, String spaceId, String title, String content);

    default String listWritableWikiLocations(String userId, String spaceId, String cursor, Integer limit) {
        throw new UnsupportedOperationException("Writable Wiki directory is unavailable");
    }
}

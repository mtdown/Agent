package com.et.agent;

/** Cloud-backed library operations performed for the authenticated chat user. */
public interface AgentLibraryGateway {
    String getSkill(String userId, String id, int version);
    String createSkill(String userId, String definition);
    String createCode(String userId, String definition);
    String writeFile(String userId, String path, String content, String mode);
    String readFile(String userId, String path);
    default String readFileSegment(String userId, String path, String cursor) { throw new UnsupportedOperationException(); }
    default String searchFile(String userId, String path, String query, String cursor) { throw new UnsupportedOperationException(); }
    String writeWikiDocument(String userId, String spaceId, String title, String content);

    default String createWikiFolder(String userId, String spaceId, String taskName) {
        throw new UnsupportedOperationException("Wiki folder creation is unavailable");
    }

    default String createWikiDocument(String userId, String spaceId, String folderId,
                                      String title, String content) {
        throw new UnsupportedOperationException("Wiki document creation is unavailable");
    }

    default String listWritableWikiLocations(String userId, String spaceId, String cursor, Integer limit) {
        throw new UnsupportedOperationException("Writable Wiki directory is unavailable");
    }
}

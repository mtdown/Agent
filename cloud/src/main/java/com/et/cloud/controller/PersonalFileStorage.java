package com.et.cloud.controller;

import java.io.Reader;

/** Internal storage boundary. Content is streamed and version checks are scoped to one local process. */
interface PersonalFileStorage {
    String read(long userId, String relative);
    String version(long userId, String relative);
    AgentPersonalFileStore.Segment segment(long userId, String relative, AgentPersonalFileStore.Cursor cursor, int maxBytes);
    AgentPersonalFileStore.SearchResult search(long userId, String relative, String query, AgentPersonalFileStore.Cursor cursor, int scanBytes, int maxResults);
    AgentPersonalFileStore.WriteResult write(long userId, String relative, Reader content, String mode, String expectedVersion);
}

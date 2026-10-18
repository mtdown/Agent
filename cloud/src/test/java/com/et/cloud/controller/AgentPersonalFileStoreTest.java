package com.et.cloud.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentPersonalFileStoreTest {
    @TempDir Path root;

    @Test void writesAndReadsOnlyWithinCurrentUsersDirectory() {
        AgentPersonalFileStore store = new AgentPersonalFileStore(root);
        assertEquals("notes/list.txt", store.write(7L, "notes/list.txt", "one", "create").path);
        assertEquals("one", store.read(7L, "notes/list.txt"));
        assertThrows(IllegalArgumentException.class, () -> store.read(8L, "notes/list.txt"));
        assertTrue(store.write(7L, "notes/list.txt", "\ntwo", "append").verified);
        assertEquals("one\ntwo", store.read(7L, "notes/list.txt"));
        assertTrue(store.write(7L, "notes/list.txt", "final", "overwrite").verified);
        assertEquals("final", store.read(7L, "notes/list.txt"));
        assertThrows(IllegalArgumentException.class, () -> store.write(7L, "notes/list.txt", "again", "create"));
    }

    @Test void rejectsPathsOutsideUserRootAndOversizedContent() {
        AgentPersonalFileStore store = new AgentPersonalFileStore(root);
        for (String path : new String[]{"../other.txt", "/etc/passwd", "C:/Windows/x", "notes/../../escape", "a\\b"})
            assertThrows(IllegalArgumentException.class, () -> store.write(7L, path, "x", "create"), path);
        assertThrows(IllegalArgumentException.class,
                () -> store.write(7L, "large.txt", "x".repeat(1_048_577), "create"));
    }

    @Test void refusesExistingSymlinkAsParent() throws Exception {
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path user = Files.createDirectory(root.resolve("7"));
        try { Files.createSymbolicLink(user.resolve("link"), outside); }
        catch (UnsupportedOperationException | java.nio.file.FileSystemException | SecurityException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "symlinks unavailable");
        }
        AgentPersonalFileStore store = new AgentPersonalFileStore(root);
        assertThrows(IllegalArgumentException.class, () -> store.write(7L, "link/x.txt", "x", "create"));
    }
}

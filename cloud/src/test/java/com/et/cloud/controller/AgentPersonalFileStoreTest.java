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
                () -> store.write(7L, "large.txt", "x".repeat(50 * 1024 * 1024 + 1), "create"));
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

    @Test void oversizeAppendPreservesOriginalBytes() throws Exception {
        AgentPersonalFileStore store = new AgentPersonalFileStore(root);
        store.write(7, "a.txt", "中文", "create");
        assertThrows(IllegalArgumentException.class, () -> store.write(7, "a.txt", "x".repeat(50 * 1024 * 1024), "append"));
        assertArrayEquals("中文".getBytes(java.nio.charset.StandardCharsets.UTF_8), Files.readAllBytes(root.resolve("7/a.txt")));
    }
    @Test void acceptsFilesAtFiftyMiBLimit() throws Exception {
        AgentPersonalFileStore store = new AgentPersonalFileStore(root);
        String content = "x".repeat(50 * 1024 * 1024 - 3) + "中";
        assertEquals(50 * 1024 * 1024, store.write(7, "limit.txt", content, "create").bytes);
        assertEquals(50 * 1024 * 1024, Files.size(root.resolve("7/limit.txt")));
    }

    @Test void failedReaderPreservesFileAndCleansTemporaryFile() throws Exception {
        AgentPersonalFileStore store = new AgentPersonalFileStore(root);
        store.write(7, "a.txt", "原文", "create");
        java.io.Reader broken = new java.io.Reader() {
            int calls;
            public int read(char[] c, int off, int len) throws java.io.IOException {
                if (calls++ == 0) { c[off] = 'x'; return 1; }
                throw new java.io.IOException("injected");
            }
            public void close() { }
        };
        assertThrows(IllegalStateException.class, () -> store.write(7, "a.txt", broken, "overwrite", null));
        assertEquals("原文", Files.readString(root.resolve("7/a.txt")));
        try (java.util.stream.Stream<Path> files = Files.list(root.resolve("7"))) { assertEquals(1, files.count()); }
    }
    @Test void competingVersionWritesAllowOnlyOne() throws Exception {
        AgentPersonalFileStore store = new AgentPersonalFileStore(root);
        store.write(7, "a.txt", "初始", "create");
        String version = store.version(7, "a.txt");
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> write = () -> {
                start.await();
                try { store.write(7, "a.txt", new java.io.StringReader("相同"), "overwrite", version); return true; }
                catch (IllegalStateException conflict) { return false; }
            };
            java.util.concurrent.Future<Boolean> a = pool.submit(write), b = pool.submit(write);
            start.countDown(); assertNotEquals(a.get(), b.get());
            assertEquals("相同", Files.readString(root.resolve("7/a.txt")));
        } finally { pool.shutdownNow(); }
    }
    @Test void differentFilesWriteWithoutWaitingForEachOther() throws Exception {
        AgentPersonalFileStore store = new AgentPersonalFileStore(root);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1), release = new java.util.concurrent.CountDownLatch(1);
        java.io.Reader blocked = new java.io.Reader() {
            public int read(char[] c, int off, int len) throws java.io.IOException {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { throw new java.io.IOException(e); }
                return -1;
            }
            public void close() { }
        };
        try {
            java.util.concurrent.Future<?> a = pool.submit(() -> store.write(7, "a.txt", blocked, "create", null));
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> store.write(7, "b.txt", "b", "create"));
            release.countDown(); a.get();
            assertEquals("b", Files.readString(root.resolve("7/b.txt")));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void failedCommitPreservesFileAndCleansTemporaryFile() throws Exception {
        AgentPersonalFileStore normal = new AgentPersonalFileStore(root);
        normal.write(7, "a.txt", "old 中文", "create");
        AgentPersonalFileStore failing = new AgentPersonalFileStore(root, (source, target) -> { throw new java.io.IOException("commit failure"); });
        assertThrows(IllegalStateException.class, () -> failing.write(7, "a.txt", "new", "overwrite"));
        assertEquals("old 中文", Files.readString(root.resolve("7/a.txt")));
        try (java.util.stream.Stream<Path> files = Files.list(root.resolve("7"))) { assertEquals(1, files.count()); }
    }

    @Test void overwriteAndAppendRespectExactByteBoundary() throws Exception {
        AgentPersonalFileStore store = new AgentPersonalFileStore(root);
        store.write(7, "a", "旧", "create");
        String near = "x".repeat(50 * 1024 * 1024 - 3);
        assertEquals(near.length(), store.write(7, "a", near, "overwrite").bytes);
        assertEquals(50 * 1024 * 1024, store.write(7, "a", "中", "append").bytes);
        assertThrows(IllegalArgumentException.class, () -> store.write(7, "a", "z", "append"));
        assertThrows(IllegalArgumentException.class, () -> store.write(7, "a", near + "中文", "overwrite"));
        Path file = root.resolve("7/a");
        assertEquals(50 * 1024 * 1024, Files.size(file));
        try (java.io.InputStream in = Files.newInputStream(file)) {
            assertEquals(50 * 1024 * 1024 - 3, in.skip(50 * 1024 * 1024 - 3));
            assertArrayEquals("中".getBytes(java.nio.charset.StandardCharsets.UTF_8), in.readAllBytes());
        }
    }

    @Test void versionSurvivesNewStoreAndChangesForAbaWrites() {
        AgentPersonalFileStore first = new AgentPersonalFileStore(root);
        first.write(7, "a", "A", "create");
        String original = first.version(7, "a");
        AgentPersonalFileStore second = new AgentPersonalFileStore(root);
        assertEquals(original, second.version(7, "a"));
        second.write(7, "a", "B", "overwrite");
        second.write(7, "a", "A", "overwrite");
        assertNotEquals(original, first.version(7, "a"));
        assertThrows(IllegalStateException.class, () -> first.write(7, "a", new java.io.StringReader("C"), "overwrite", original));
    }

    @Test void refusesWindowsDirectoryJunctionEscape() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path user = Files.createDirectory(root.resolve("7"));
        Path link = user.resolve("link");
        Process junction = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), outside.toString())
                .redirectErrorStream(true).start();
        String output = new String(junction.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, junction.waitFor(), output);
        AgentPersonalFileStore store = new AgentPersonalFileStore(root);
        assertThrows(IllegalArgumentException.class, () -> store.write(7, "link/escape.txt", "x", "create"));
        assertFalse(Files.exists(outside.resolve("escape.txt")));
        Files.writeString(outside.resolve("existing.txt"), "original", java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> store.read(7, "link/existing.txt"));
        assertThrows(IllegalArgumentException.class, () -> store.write(7, "link/existing.txt", "changed", "overwrite"));
        assertEquals("original", Files.readString(outside.resolve("existing.txt"), java.nio.charset.StandardCharsets.UTF_8));
        Process userJunction = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", root.resolve("8").toString(), outside.toString())
                .redirectErrorStream(true).start();
        String userOutput = new String(userJunction.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, userJunction.waitFor(), userOutput);
        assertThrows(IllegalArgumentException.class, () -> store.write(8, "escape.txt", "x", "create"));
        assertThrows(IllegalArgumentException.class, () -> store.read(8, "existing.txt"));
        assertFalse(Files.exists(outside.resolve("escape.txt")));
    }
}

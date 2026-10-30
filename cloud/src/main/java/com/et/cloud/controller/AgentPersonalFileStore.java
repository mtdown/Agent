package com.et.cloud.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.StandardCopyOption;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;

/** UTF-8 files scoped to one user's persistent Cloud workspace. */
@Component
public final class AgentPersonalFileStore implements PersonalFileStorage {
    private static final int MAX_BYTES = 50 * 1024 * 1024;
    private static final int MAX_LEGACY_READ_BYTES = 1_048_576;
    private static final ConcurrentHashMap<Path, LockRef> LOCKS = new ConcurrentHashMap<>();
    private static final class LockRef { int users; }
    private final Path root;
    private final Committer committer;
    interface Committer { void commit(Path source, Path target) throws IOException; }

    @Autowired
    public AgentPersonalFileStore(@Value("${agent.workspace-root:tmp/agent-user-files}") String root) {
        this(Path.of(root));
    }

    AgentPersonalFileStore(Path root) { this(root, (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)); }
    AgentPersonalFileStore(Path root, Committer committer) { this.root = root.toAbsolutePath().normalize(); this.committer = committer; }

    public static final class WriteResult {
        public final String path;
        public final int bytes;
        public final boolean verified;

        WriteResult(String path, int bytes, boolean verified) {
            this.path = path; this.bytes = bytes; this.verified = verified;
        }
    }

    public WriteResult write(long userId, String relative, String content, String mode) {
        if (content == null) throw new IllegalArgumentException("Invalid file content");
        return write(userId, relative, new StringReader(content), mode, null);
    }

    /** Internal conditional write; a null expected version retains the legacy behavior. */
    public WriteResult write(long userId, String relative, Reader content, String mode, String expectedVersion) {
        if (content == null || mode == null || !Set.of("create", "overwrite", "append").contains(mode))
            throw new IllegalArgumentException("Invalid file content or write mode");
        Path key = root.resolve(Long.toString(userId)).resolve(relative == null ? "" : relative).normalize();
        LockRef lock = LOCKS.compute(key, (k, old) -> { LockRef ref = old == null ? new LockRef() : old; ref.users++; return ref; });
        try {
            synchronized (lock) { return writeLocked(userId, relative, content, mode, expectedVersion); }
        } finally {
            LOCKS.compute(key, (k, ref) -> { ref.users--; return ref.users == 0 ? null : ref; });
        }
    }

    void validatePath(long userId, String relative) { resolve(userId, relative, false); }

    public String version(long userId, String relative) {
        Path target = resolve(userId, relative, false);
        LockRef lock = LOCKS.compute(target, (k, old) -> { LockRef ref = old == null ? new LockRef() : old; ref.users++; return ref; });
        try {
            synchronized (lock) { return digest(target); }
        } catch (IOException e) { throw new IllegalStateException("Personal file version failed", e); }
        finally { LOCKS.compute(target, (k, ref) -> { ref.users--; return ref.users == 0 ? null : ref; }); }
    }

    private WriteResult writeLocked(long userId, String relative, Reader content, String mode, String expectedVersion) {
        Path target = resolve(userId, relative, true);
        Path temporary = null;
        try {
            boolean exists = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
            if ("create".equals(mode) && Files.exists(target, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("File already exists: " + relative);
            if (!"create".equals(mode) && !exists)
                throw new IllegalArgumentException("File does not exist: " + relative);
            if (expectedVersion != null && (!exists || !expectedVersion.equals(digest(target))))
                throw new IllegalStateException("Personal file version conflict");
            temporary = Files.createTempFile(target.getParent(), ".agent-write-", ".tmp");
            try (OutputStream raw = Files.newOutputStream(temporary); OutputStream bounded = new BoundedOutputStream(raw)) {
                if ("append".equals(mode)) {
                    try (InputStream old = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) { old.transferTo(bounded); }
                }
                Writer writer = new OutputStreamWriter(bounded, StandardCharsets.UTF_8);
                char[] chars = new char[8192];
                int n;
                while ((n = content.read(chars)) != -1) writer.write(chars, 0, n);
                writer.close();
            }
            int bytes = (int) Files.size(temporary);
            if (exists) {
                FileTime oldTime = Files.getLastModifiedTime(target, LinkOption.NOFOLLOW_LINKS);
                long desired;
                try { desired = Math.max(System.currentTimeMillis(), Math.addExact(oldTime.toMillis(), 2000)); }
                catch (ArithmeticException e) { throw new IOException("File version time exhausted", e); }
                Files.setLastModifiedTime(temporary, FileTime.fromMillis(desired));
                if (Files.getLastModifiedTime(temporary, LinkOption.NOFOLLOW_LINKS).compareTo(oldTime) <= 0)
                    throw new IOException("File system cannot advance version time");
            }
            // Recheck path before committing; atomic replacement is required, never fall back to a direct write.
            resolve(userId, relative, false);
            committer.commit(temporary, target);
            temporary = null;
            return new WriteResult(relative, bytes, true);
        } catch (IOException e) {
            throw new IllegalStateException("Personal file write failed", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException e) { throw new IllegalStateException("Personal file temporary cleanup failed", e); }
            }
        }
    }

    private static String digest(Path target) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            }
            digest.update(Files.getLastModifiedTime(target, LinkOption.NOFOLLOW_LINKS).toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static final class BoundedOutputStream extends OutputStream {
        private final OutputStream out; private long count;
        BoundedOutputStream(OutputStream out) { this.out = out; }
        @Override public void write(int b) throws IOException { check(1); out.write(b); }
        @Override public void write(byte[] b, int off, int len) throws IOException { check(len); out.write(b, off, len); }
        private void check(int length) { if (count + length > MAX_BYTES) throw new IllegalArgumentException("File content exceeds 50 MiB"); count += length; }
        @Override public void close() throws IOException { out.close(); }
        @Override public void flush() throws IOException { out.flush(); }
    }

    public String read(long userId, String relative) {
        Path target = resolve(userId, relative, false);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("File does not exist: " + relative);
        try (SeekableByteChannel channel = Files.newByteChannel(target,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            if (channel.size() > MAX_LEGACY_READ_BYTES) throw new IllegalArgumentException("File content exceeds 1 MiB");
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
            return StandardCharsets.UTF_8.decode((ByteBuffer) buffer.flip()).toString();
        } catch (IOException e) {
            throw new IllegalStateException("Personal file read failed", e);
        }
    }

    // Opaque in-process continuation: the caller cannot alter its position or binding.
    public static final class Cursor {
        private final long user, position;
        private final String path, version, query;
        private Cursor(long user, String path, String version, long position, String query) {
            this.user = user; this.path = path; this.version = version; this.position = position; this.query = query;
        }
    }
    // Authenticated, stateless continuation; the key is domain-separated from the service credential.
    static String encodeCursor(Cursor cursor, String secret) {
        if (cursor == null) return null;
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
            out.writeLong(cursor.user); out.writeUTF(cursor.path); out.writeUTF(cursor.version);
            out.writeLong(cursor.position); out.writeBoolean(cursor.query != null);
            if (cursor.query != null) out.writeUTF(cursor.query);
            byte[] data = bytes.toByteArray();
            byte[] mac = cursorMac(secret, data);
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data) + "." +
                    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid personal file cursor"); }
    }
    static Cursor decodeCursor(String token, String secret) {
        if (token == null) return null;
        try {
            if (token.length() > 8192) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            byte[] data = java.util.Base64.getUrlDecoder().decode(parts[0]);
            byte[] mac = java.util.Base64.getUrlDecoder().decode(parts[1]);
            if (!java.security.MessageDigest.isEqual(mac, cursorMac(secret, data))) throw new IllegalArgumentException();
            java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(data));
            long user = in.readLong(); String path = in.readUTF(), version = in.readUTF(); long pos = in.readLong();
            String query = in.readBoolean() ? in.readUTF() : null;
            if (in.available() != 0) throw new IllegalArgumentException();
            return new Cursor(user, path, version, pos, query);
        } catch (Exception e) { throw new IllegalArgumentException("Invalid personal file cursor"); }
    }
    private static byte[] cursorMac(String secret, byte[] data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update("agent-personal-file-cursor-v1\0".getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(data);
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Cursor authentication unavailable"); }
    }
    public static final class Segment {
        public final String text, version;
        public final long start;
        public final Cursor next;
        private Segment(String text, String version, long start, Cursor next) {
            this.text = text; this.version = version; this.start = start; this.next = next;
        }
    }
    public static final class SearchResult {
        public final List<Long> offsets;
        public final String version;
        public final Cursor next;
        private SearchResult(List<Long> offsets, String version, Cursor next) {
            this.offsets = List.copyOf(offsets); this.version = version; this.next = next;
        }
    }
    private interface LockedOperation<T> { T run(Path path) throws IOException; }
    private <T> T locked(long user, String relative, LockedOperation<T> operation) {
        Path key = root.resolve(Long.toString(user)).resolve(relative == null ? "" : relative).normalize();
        LockRef lock = LOCKS.compute(key, (k, old) -> { LockRef ref = old == null ? new LockRef() : old; ref.users++; return ref; });
        try {
            synchronized (lock) {
                Path target = resolve(user, relative, false);
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("File does not exist");
                return operation.run(target);
            }
        } catch (IOException e) { throw new IllegalStateException("Personal file read failed", e); }
        finally { LOCKS.compute(key, (k, ref) -> { ref.users--; return ref.users == 0 ? null : ref; }); }
    }
    private static long position(Cursor c, long user, String path, String version, String query, long size) {
        if (c == null) return 0;
        if (c.user != user || !c.path.equals(path) || !java.util.Objects.equals(c.query, query)
                || c.position < 0) throw new IllegalArgumentException("Invalid personal file cursor");
        if (!c.version.equals(version)) throw new IllegalStateException("Personal file version conflict");
        if (c.position > size) throw new IllegalArgumentException("Invalid personal file cursor");
        return c.position;
    }
    private static byte[] readBytes(SeekableByteChannel channel, long position, int count) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(count); channel.position(position);
        while (buf.hasRemaining() && channel.read(buf) != -1) { }
        return java.util.Arrays.copyOf(buf.array(), buf.position());
    }
    private static String decode(byte[] bytes, int length) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, 0, length)).toString();
    }
    @Override public Segment segment(long user, String relative, Cursor cursor, int maxBytes) {
        if (maxBytes < 4 || maxBytes > 1024 * 1024) throw new IllegalArgumentException("Invalid segment limit");
        return locked(user, relative, path -> {
            String version = digest(path);
            try (SeekableByteChannel ch = Files.newByteChannel(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                long size = ch.size(), start = position(cursor, user, relative, version, null, size);
                byte[] bytes = readBytes(ch, start, (int) Math.min(maxBytes, size - start));
                int end = bytes.length;
                // Remove a partial trailing code point, retaining only complete UTF-8 bytes.
                if (start + end < size && end > 0) {
                    int lead = end - 1;
                    while (lead >= 0 && (bytes[lead] & 0xc0) == 0x80) lead--;
                    if (lead >= 0) {
                        int b = bytes[lead] & 0xff;
                        int width = b < 0x80 ? 1 : b >= 0xc2 && b <= 0xdf ? 2 : b >= 0xe0 && b <= 0xef ? 3 : b >= 0xf0 && b <= 0xf4 ? 4 : 1;
                        if (end - lead < width) end = lead;
                    }
                }
                String text = decode(bytes, end);
                long nextPos = start + end;
                Cursor next = nextPos < size ? new Cursor(user, relative, version, nextPos, null) : null;
                return new Segment(text, version, start, next);
            }
        });
    }
    Segment previewSegment(long user, String relative, String version, long start, int maxBytes) {
        if (version == null || start < 0 || maxBytes < 1) throw new IllegalArgumentException("Invalid preview position");
        try {
            Segment result = segment(user, relative, new Cursor(user, relative, version, start, null), Math.max(4, maxBytes));
            if (maxBytes >= 4) return result;
            byte[] bytes = result.text.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(maxBytes, bytes.length);
            String text = decode(bytes, length);
            Cursor next = length < bytes.length || result.next != null ? new Cursor(user, relative, version, start + length, null) : null;
            return new Segment(text, version, start, next);
        }
        catch (IllegalStateException e) {
            if (e.getCause() instanceof CharacterCodingException) throw new IllegalArgumentException("Invalid preview position");
            throw e;
        } catch (CharacterCodingException e) { throw new IllegalArgumentException("Invalid preview position"); }
    }
    long previewBoundary(long user, String relative, String version, long wanted) {
        if (wanted < 0) throw new IllegalArgumentException("Invalid preview position");
        return locked(user, relative, path -> {
            if (!digest(path).equals(version)) throw new IllegalStateException("Personal file version conflict");
            try (SeekableByteChannel ch = Files.newByteChannel(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                if (wanted > ch.size()) throw new IllegalArgumentException("Invalid preview position");
                long pos = wanted;
                while (pos > 0 && pos < ch.size() && (readBytes(ch, pos, 1)[0] & 0xc0) == 0x80) pos--;
                return pos;
            }
        });
    }
    @Override public SearchResult search(long user, String relative, String query, Cursor cursor, int scanBytes, int maxResults) {
        if (query == null || query.isEmpty() || scanBytes < 1 || scanBytes > 65536 || maxResults < 1 || maxResults > 100)
            throw new IllegalArgumentException("Invalid search parameters");
        final byte[] pattern;
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(query));
            if (encoded.remaining() > 1024) throw new IllegalArgumentException("Search query too long");
            pattern = new byte[encoded.remaining()]; encoded.get(pattern);
        } catch (CharacterCodingException e) { throw new IllegalArgumentException("Invalid search query", e); }
        return locked(user, relative, path -> {
            String version = digest(path);
            try (SeekableByteChannel ch = Files.newByteChannel(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                long size = ch.size(), start = position(cursor, user, relative, version, query, size);
                int candidates = (int) Math.min(scanBytes, size - start);
                byte[] bytes = readBytes(ch, start, (int) Math.min((long) candidates + pattern.length - 1, size - start));
                List<Long> matches = new ArrayList<>(); long nextPos = start + candidates;
                for (int i = 0; i < candidates && i + pattern.length <= bytes.length; i++) {
                    int j = 0; while (j < pattern.length && bytes[i+j] == pattern[j]) j++;
                    if (j == pattern.length) {
                        matches.add(start + i);
                        if (matches.size() == maxResults) { nextPos = start + i + 1; break; }
                    }
                }
                Cursor next = nextPos < size ? new Cursor(user, relative, version, nextPos, query) : null;
                return new SearchResult(matches, version, next);
            }
        });
    }

    private Path resolve(long userId, String relative, boolean createParents) {
        if (userId < 1 || relative == null || relative.isBlank() || relative.length() > 512
                || relative.startsWith("/") || relative.contains("\\") || relative.contains(":")
                || relative.chars().anyMatch(ch -> ch < 32))
            throw new IllegalArgumentException("Invalid personal file path");
        String[] parts = relative.split("/", -1);
        for (String part : parts)
            if (part.isEmpty() || ".".equals(part) || "..".equals(part) || part.endsWith(".") || part.endsWith(" "))
                throw new IllegalArgumentException("Invalid personal file path");
        Path user = root.resolve(Long.toString(userId));
        Path target = user.resolve(relative).normalize();
        if (!target.startsWith(user)) throw new IllegalArgumentException("Path escapes personal space");
        try {
            if (Files.isSymbolicLink(root)) throw new IllegalArgumentException("Symbolic links are not allowed");
            if (createParents) Files.createDirectories(user);
            Path current = user;
            rejectRedirect(current);
            for (int i = 0; i < parts.length - 1; i++) {
                current = current.resolve(parts[i]);
                rejectRedirect(current);
                if (createParents && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    try { Files.createDirectory(current); } catch (FileAlreadyExistsException ignored) { /* another file created the parent */ }
                }
                rejectRedirect(current);
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
                    throw new IllegalArgumentException("Parent path is not a directory");
            }
            rejectRedirect(target);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Personal file path failed", e);
        }
    }
    /** Reject links and directory junctions whose resolved location differs from the direct child path. */
    private static void rejectRedirect(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("Symbolic links are not allowed");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            Path parentReal = path.getParent().toRealPath();
            if (!path.toRealPath().equals(parentReal.resolve(path.getFileName())))
                throw new IllegalArgumentException("Redirected paths are not allowed");
        }
    }
}

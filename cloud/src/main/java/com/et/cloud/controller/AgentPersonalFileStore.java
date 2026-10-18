package com.et.cloud.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/** UTF-8 files scoped to one user's persistent Cloud workspace. */
@Component
public final class AgentPersonalFileStore {
    private static final int MAX_BYTES = 1_048_576;
    private final Path root;

    @Autowired
    public AgentPersonalFileStore(@Value("${agent.workspace-root:tmp/agent-user-files}") String root) {
        this(Path.of(root));
    }

    AgentPersonalFileStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public static final class WriteResult {
        public final String path;
        public final int bytes;
        public final boolean verified;

        WriteResult(String path, int bytes, boolean verified) {
            this.path = path; this.bytes = bytes; this.verified = verified;
        }
    }

    public synchronized WriteResult write(long userId, String relative, String content, String mode) {
        if (content == null || !Set.of("create", "overwrite", "append").contains(mode))
            throw new IllegalArgumentException("Invalid file content or write mode");
        byte[] input = content.getBytes(StandardCharsets.UTF_8);
        if (input.length > MAX_BYTES) throw new IllegalArgumentException("File content exceeds 1 MiB");
        Path target = resolve(userId, relative, true);
        try {
            if ("create".equals(mode)) {
                Files.write(target, input, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS);
            } else {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
                    throw new IllegalArgumentException("File does not exist: " + relative);
                if ("append".equals(mode) && Files.size(target) + input.length > MAX_BYTES)
                    throw new IllegalArgumentException("File content exceeds 1 MiB");
                Files.write(target, input, StandardOpenOption.WRITE,
                        "append".equals(mode) ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING,
                        LinkOption.NOFOLLOW_LINKS);
            }
            String actual = read(userId, relative);
            boolean verified = "append".equals(mode) ? actual.endsWith(content) : actual.equals(content);
            if (!verified) throw new IllegalStateException("Written file could not be verified");
            return new WriteResult(relative, actual.getBytes(StandardCharsets.UTF_8).length, true);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalArgumentException("File already exists: " + relative, e);
        } catch (IOException e) {
            throw new IllegalStateException("Personal file write failed", e);
        }
    }

    public String read(long userId, String relative) {
        Path target = resolve(userId, relative, false);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("File does not exist: " + relative);
        try (SeekableByteChannel channel = Files.newByteChannel(target,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            if (channel.size() > MAX_BYTES) throw new IllegalArgumentException("File content exceeds 1 MiB");
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
            return StandardCharsets.UTF_8.decode((ByteBuffer) buffer.flip()).toString();
        } catch (IOException e) {
            throw new IllegalStateException("Personal file read failed", e);
        }
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
            if (createParents) Files.createDirectories(user);
            Path current = user;
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("Symbolic links are not allowed");
            for (int i = 0; i < parts.length - 1; i++) {
                current = current.resolve(parts[i]);
                if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("Symbolic links are not allowed");
                if (createParents && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(current);
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
                    throw new IllegalArgumentException("Parent path is not a directory");
            }
            if (Files.isSymbolicLink(target)) throw new IllegalArgumentException("Symbolic links are not allowed");
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Personal file path failed", e);
        }
    }
}

package com.et.cloud.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;

/** Process-local upload sessions. Temporary content never becomes visible as a personal file. */
@Service
class AgentPersonalFileUpload {
 static final long MAX = 50L * 1024 * 1024;
 static final int PART = 1024 * 1024;
 static final long TTL = 60L * 60 * 1000;
 private final AgentPersonalFileStore store;
 private final Path dir;
 private final Clock clock;
 private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
 private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "personal-upload-cleaner"); t.setDaemon(true); return t; });
 static class Session {
  final long user, size; final String path, mode, version; final Path temp;
  long offset, touched; boolean closed;
  Session(long user, long size, String path, String mode, String version, Path temp, long now) { this.user=user; this.size=size; this.path=path; this.mode=mode; this.version=version; this.temp=temp; this.touched=now; }
 }
 @org.springframework.beans.factory.annotation.Autowired
 AgentPersonalFileUpload(AgentPersonalFileStore store, @Value("${agent.upload-temp-root:tmp/agent-upload-sessions}") String dir) { this(store, Path.of(dir)); }
 AgentPersonalFileUpload(AgentPersonalFileStore store, Path dir) { this(store, dir, Clock.systemUTC()); }
 AgentPersonalFileUpload(AgentPersonalFileStore store, Path dir, Clock clock) {
  this.store=store; this.dir=dir.toAbsolutePath().normalize(); this.clock=clock;
  try { Files.createDirectories(this.dir); if (Files.isSymbolicLink(this.dir)) throw new IOException();
   try (DirectoryStream<Path> files = Files.newDirectoryStream(this.dir, "upload-*.tmp")) { for (Path p : files) Files.deleteIfExists(p); }
  } catch (IOException e) { throw new IllegalStateException("Upload storage unavailable"); }
  cleaner.scheduleWithFixedDelay(this::expire, 1, 1, TimeUnit.MINUTES);
 }
 String begin(long user, String path, String mode, long size) {
  if (user < 1 || size < 0 || size > MAX || !Set.of("create", "overwrite", "append").contains(mode)) throw new IllegalArgumentException("Invalid upload");
  // The store validates the path and captures the exact version for non-create writes.
  String version = "create".equals(mode) ? null : store.version(user, path);
  if ("create".equals(mode)) store.validatePath(user, path);
  try { Path temp=Files.createTempFile(dir, "upload-", ".tmp"); String id=UUID.randomUUID().toString();
   sessions.put(id, new Session(user,size,path,mode,version,temp,clock.millis())); return id;
  } catch (IOException e) { throw new IllegalStateException("Upload storage unavailable"); }
 }
 private Session session(long user, String id) {
  Session s = id == null ? null : sessions.get(id);
  if (s == null || s.user != user) throw new IllegalArgumentException("Upload unavailable");
  return s;
 }
 void part(long user, String id, long offset, InputStream input) {
  Session s=session(user,id); synchronized(s) {
   check(s,id); if (offset != s.offset || input == null || s.offset >= s.size) throw new IllegalArgumentException("Invalid upload segment");
   long count=0;
   try (OutputStream out=Files.newOutputStream(s.temp, StandardOpenOption.APPEND)) {
    byte[] buffer=new byte[8192]; int n;
    while ((n=input.read(buffer)) != -1) { if (n == 0) continue; count+=n; if (count > PART || count > s.size-s.offset) throw new IllegalArgumentException("Invalid upload segment"); out.write(buffer,0,n); }
    if (count == 0) throw new IllegalArgumentException("Invalid upload segment");
    s.offset+=count; s.touched=clock.millis();
   } catch (IOException | RuntimeException e) { discard(id,s); throw new IllegalArgumentException("Upload segment failed"); }
  }
 }
 AgentPersonalFileStore.WriteResult commit(long user, String id) {
  Session s=session(user,id); synchronized(s) {
   check(s,id); if (s.offset != s.size) { discard(id,s); throw new IllegalArgumentException("Incomplete upload"); }
   try (Reader in = new InputStreamReader(Files.newInputStream(s.temp), StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))) {
    return store.write(user,s.path,in,s.mode,s.version);
   } catch (IOException e) { throw new IllegalArgumentException("Invalid upload content"); }
   finally { discard(id,s); }
  }
 }
 void cancel(long user,String id) { Session s=session(user,id); synchronized(s) { check(s,id); discard(id,s); } }
 private void check(Session s,String id) { if (s.closed || clock.millis()-s.touched >= TTL) { discard(id,s); throw new IllegalArgumentException("Upload unavailable"); } }
 private void discard(String id,Session s) { s.closed=true; try { Files.deleteIfExists(s.temp); sessions.remove(id,s); } catch (IOException e) { throw new IllegalStateException("Upload cleanup failed"); } }
 void expire() { long now=clock.millis(); sessions.forEach((id,s) -> { synchronized(s) { if (s.closed || now-s.touched >= TTL) { try { discard(id,s); } catch (RuntimeException ignored) { /* retry at shutdown or startup */ } } } }); }
 @PreDestroy void close() { cleaner.shutdownNow(); sessions.forEach((id,s) -> { synchronized(s) { discard(id,s); } }); }
}

package com.et.cloud.controller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AgentPersonalFileUploadTest {
 @TempDir Path root;
 static InputStream bytes(String s) { return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)); }
 @Test void modesIsolationAndOrder() throws Exception {
  AgentPersonalFileStore store=new AgentPersonalFileStore(root.resolve("files"));
  AgentPersonalFileUpload upload=new AgentPersonalFileUpload(store,root.resolve("uploads"));
  String id=upload.begin(7,"a","create",3); final String initial=id;
  assertThrows(IllegalArgumentException.class,()->upload.part(8,initial,0,bytes("x")));
  upload.part(7,id,0,bytes("abc"));
  assertThrows(IllegalArgumentException.class,()->upload.part(7,initial,0,bytes("x")));
  assertThrows(IllegalArgumentException.class,()->upload.commit(8,initial));
  upload.commit(7,id); assertEquals("abc",store.read(7,"a"));
  id=upload.begin(7,"a","overwrite",3); upload.part(7,id,0,bytes("中")); upload.commit(7,id);
  id=upload.begin(7,"a","append",1); upload.part(7,id,0,bytes("!")); upload.commit(7,id);
  assertEquals("中!",store.read(7,"a")); upload.close();
 }
 @Test void invalidIncompleteConflictDisconnectAndCancelPreserveOriginal() throws Exception {
  AgentPersonalFileStore store=new AgentPersonalFileStore(root.resolve("files")); store.write(7,"a","original","create");
  Path dir=root.resolve("uploads"); AgentPersonalFileUpload upload=new AgentPersonalFileUpload(store,dir);
  assertThrows(IllegalArgumentException.class,()->upload.begin(7,"../escape","create",1));
  assertThrows(IllegalArgumentException.class,()->upload.begin(7,"a","overwrite",AgentPersonalFileUpload.MAX+1));
  String invalid=upload.begin(7,"a","overwrite",2); upload.part(7,invalid,0,new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28}));
  assertThrows(RuntimeException.class,()->upload.commit(7,invalid));
  String incomplete=upload.begin(7,"a","overwrite",2); upload.part(7,incomplete,0,bytes("x")); assertThrows(IllegalArgumentException.class,()->upload.commit(7,incomplete));
  String conflict=upload.begin(7,"a","overwrite",1); upload.part(7,conflict,0,bytes("x")); store.write(7,"a","changed","overwrite");
  assertThrows(IllegalStateException.class,()->upload.commit(7,conflict)); assertEquals("changed",store.read(7,"a"));
  String cancel=upload.begin(7,"a","overwrite",1); upload.cancel(7,cancel);
  String broken=upload.begin(7,"a","overwrite",1); InputStream fail=new InputStream(){ public int read() throws IOException { throw new IOException("disconnect"); }};
  assertThrows(IllegalArgumentException.class,()->upload.part(7,broken,0,fail));
  try (java.util.stream.Stream<Path> paths=Files.list(dir)) { assertEquals(0,paths.count()); } upload.close();
 }
 @Test void commitsFullFiftyMiBInBoundedParts() throws Exception {
  AgentPersonalFileStore store=new AgentPersonalFileStore(root.resolve("files")); AgentPersonalFileUpload upload=new AgentPersonalFileUpload(store,root.resolve("uploads"));
  String id=upload.begin(7,"large","create",AgentPersonalFileUpload.MAX); byte[] block=new byte[AgentPersonalFileUpload.PART]; java.util.Arrays.fill(block,(byte)'x');
  for(int i=0;i<50;i++) upload.part(7,id,(long)i*block.length,new ByteArrayInputStream(block));
  assertEquals(AgentPersonalFileUpload.MAX,upload.commit(7,id).bytes);
  try(InputStream in=Files.newInputStream(root.resolve("files/7/large"))) { byte[] got=new byte[8192]; long total=0; int n; while((n=in.read(got))!=-1) { for(int i=0;i<n;i++) assertEquals('x',got[i]); total+=n; } assertEquals(AgentPersonalFileUpload.MAX,total); } upload.close();
 }
 @Test void expiredSessionIsRemoved() throws Exception {
  class MutableClock extends Clock { long now; public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return this;} public Instant instant(){return Instant.ofEpochMilli(now);} }
  MutableClock clock=new MutableClock(); Path dir=root.resolve("uploads"); AgentPersonalFileUpload upload=new AgentPersonalFileUpload(new AgentPersonalFileStore(root.resolve("files")),dir,clock);
  String id=upload.begin(1,"x","create",1); clock.now=AgentPersonalFileUpload.TTL; upload.expire();
  assertThrows(IllegalArgumentException.class,()->upload.commit(1,id)); try(java.util.stream.Stream<Path> p=Files.list(dir)){assertEquals(0,p.count());} upload.close();
 }
 @Test void oversizedSegmentAndCommitFailureLeaveOriginalAndRemoveTemporaryFiles() throws Exception {
  Path files=root.resolve("files"), dir=root.resolve("uploads"); AgentPersonalFileStore normal=new AgentPersonalFileStore(files); normal.write(3,"a","original","create");
  AgentPersonalFileStore failing=new AgentPersonalFileStore(files,(source,target)->{throw new IOException("injected");});
  AgentPersonalFileUpload upload=new AgentPersonalFileUpload(failing,dir);
  String oversized=upload.begin(3,"a","overwrite",AgentPersonalFileUpload.PART+1);
  assertThrows(IllegalArgumentException.class,()->upload.part(3,oversized,0,new ByteArrayInputStream(new byte[AgentPersonalFileUpload.PART+1])));
  String commit=upload.begin(3,"a","overwrite",1); upload.part(3,commit,0,bytes("x")); assertThrows(IllegalStateException.class,()->upload.commit(3,commit));
  assertEquals("original",normal.read(3,"a")); try(java.util.stream.Stream<Path> p=Files.list(dir)){assertEquals(0,p.count());} upload.close();
 }
 @Test void controllerChecksLoginOnEveryOperation() throws Exception {
  com.et.cloud.service.UserService users=mock(com.et.cloud.service.UserService.class); javax.servlet.http.HttpServletRequest request=mock(javax.servlet.http.HttpServletRequest.class);
  AgentPersonalFileUpload upload=new AgentPersonalFileUpload(new AgentPersonalFileStore(root.resolve("files")),root.resolve("uploads"));
  AgentPersonalFileUploadController c=new AgentPersonalFileUploadController(users,upload); when(users.getLoginUser(request)).thenThrow(new IllegalStateException("login"));
  assertThrows(IllegalStateException.class,()->c.begin(new AgentPersonalFileUploadController.Begin(),request));
  assertThrows(IllegalStateException.class,()->c.part("x",0,request)); assertThrows(IllegalStateException.class,()->c.commit("x",request)); assertThrows(IllegalStateException.class,()->c.cancel("x",request)); verify(users,times(4)).getLoginUser(request); upload.close();
 }
}

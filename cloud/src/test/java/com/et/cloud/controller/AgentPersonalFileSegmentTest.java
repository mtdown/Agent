package com.et.cloud.controller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AgentPersonalFileSegmentTest {
 @TempDir Path root;
 @Test void segmentsRestoreUtf8AndRejectChangedOrMismatchedCursor() {
  AgentPersonalFileStore s = new AgentPersonalFileStore(root); String text = "ab中🙂".repeat(30000);
  s.write(1,"a",text,"create"); s.write(2,"a",text,"create");
  AgentPersonalFileStore.Segment p=s.segment(1,"a",null,7); AgentPersonalFileStore.Cursor initial=p.next;
  assertThrows(IllegalArgumentException.class,()->s.segment(2,"a",initial,7));
  StringBuilder result=new StringBuilder(p.text);
  while(p.next!=null){p=s.segment(1,"a",p.next,65536);result.append(p.text);}
  assertArrayEquals(text.getBytes(StandardCharsets.UTF_8),result.toString().getBytes(StandardCharsets.UTF_8));
  assertEquals(text,s.read(1,"a"));
  assertThrows(IllegalArgumentException.class,()->s.segment(1,"a",null,3));
  assertThrows(IllegalArgumentException.class,()->s.segment(1,"a",null,1048577));
  s.write(1,"a","new","overwrite"); assertThrows(IllegalStateException.class,()->s.segment(1,"a",initial,7));
 }
 @Test void searchesAcrossBoundariesWithPaginationAndVersionChecks() {
  AgentPersonalFileStore s=new AgentPersonalFileStore(root); s.write(1,"a","xx中文中文x中文xxx中文","create");
  List<Long> found=new ArrayList<>(); AgentPersonalFileStore.Cursor cursor=null;
  do { AgentPersonalFileStore.SearchResult page=s.search(1,"a","中文",cursor,5,1);found.addAll(page.offsets);cursor=page.next; } while(cursor!=null);
  assertEquals(List.of(2L,8L,15L,24L),found);
  assertTrue(s.search(1,"a","missing",null,100,2).offsets.isEmpty());
  AgentPersonalFileStore.Cursor next=s.search(1,"a","中文",null,5,1).next;
  assertThrows(IllegalArgumentException.class,()->s.search(1,"a","other",next,5,1));
  s.write(1,"a","changed","overwrite"); assertThrows(IllegalStateException.class,()->s.search(1,"a","中文",next,5,1));
 }
 @Test void overlappingMatchesAndLimits() {
  AgentPersonalFileStore s=new AgentPersonalFileStore(root); s.write(1,"a","aaaaa", "create");
  List<Long> found=new ArrayList<>(); AgentPersonalFileStore.Cursor cursor=null;
  do { AgentPersonalFileStore.SearchResult page=s.search(1,"a","aa",cursor,2,1); found.addAll(page.offsets);cursor=page.next; } while(cursor!=null);
  assertEquals(List.of(0L,1L,2L,3L),found);
  assertThrows(IllegalArgumentException.class,()->s.search(1,"a","aa",null,65537,1));
  assertThrows(IllegalArgumentException.class,()->s.search(1,"a","aa",null,1,101));
  assertThrows(IllegalArgumentException.class,()->s.search(1,"../a","aa",null,1,1));
 }
 @Test void longLineSearchIsBoundedAndResumable() {
  AgentPersonalFileStore s=new AgentPersonalFileStore(root);
  s.write(1,"long", "x".repeat(65535)+"中文"+"x".repeat(140000)+"中文", "create");
  List<Long> found=new ArrayList<>(); AgentPersonalFileStore.Cursor cursor=null; int pages=0;
  do {AgentPersonalFileStore.SearchResult page=s.search(1,"long","中文",cursor,65536,2);found.addAll(page.offsets);cursor=page.next;pages++;}while(cursor!=null);
  assertEquals(List.of(65535L,205541L),found);assertTrue(pages>=4);
 }
 @Test void largeFileCanBeReadInBoundedSegments() throws Exception {
  Path user=Files.createDirectories(root.resolve("1")); byte[] block=("x".repeat(1024*1024-3)+"中").getBytes(StandardCharsets.UTF_8);
  try(java.io.OutputStream out=Files.newOutputStream(user.resolve("large"))){for(int i=0;i<50;i++)out.write(block);}
  AgentPersonalFileStore s=new AgentPersonalFileStore(root); AgentPersonalFileStore.Cursor cursor=null; long total=0; int count=0;
  do {AgentPersonalFileStore.Segment p=s.segment(1,"large",cursor,1024*1024);byte[] bytes=p.text.getBytes(StandardCharsets.UTF_8);assertTrue(bytes.length<=1024*1024);assertArrayEquals(block,bytes);total+=bytes.length;count++;cursor=p.next;}while(cursor!=null);
  assertEquals(50L*1024*1024,total); assertEquals(50,count);
 }
}

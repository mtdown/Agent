package com.et.cloud.controller;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
public class LargeProbe {
 public static void main(String[] args) throws Exception {
  Path root=Paths.get(args[0]); Path dir=Files.createDirectories(root.resolve("1")); Path f=dir.resolve("large.txt");
  byte[] block=new byte[8192]; java.util.Arrays.fill(block,(byte)'a');
  try(OutputStream out=Files.newOutputStream(f)){for(int i=0;i<3200;i++)out.write(block); out.write("\u7532\u4e59\ud83d\ude00".getBytes(StandardCharsets.UTF_8)); for(int i=0;i<3199;i++)out.write(block); out.write(block,0,8192-10);}
  long size=Files.size(f); if(size!=50L*1024*1024) throw new AssertionError(size);
  AgentPersonalFileStore s=new AgentPersonalFileStore(root); String v=s.version(1,"large.txt"); long at=3200L*8192;
  var p=s.previewSegment(1,"large.txt",v,at,16384); byte[] a=p.text.getBytes(StandardCharsets.UTF_8);
  if(!p.text.startsWith("\u7532\u4e59\ud83d\ude00")||a.length>16384||p.start!=at||p.next==null) throw new AssertionError("initial");
  long before=s.previewBoundary(1,"large.txt",v,at-16384); var prev=s.previewSegment(1,"large.txt",v,before,(int)(at-before));
  var next=s.previewSegment(1,"large.txt",v,at+a.length,16384);
  if(before+prev.text.getBytes(StandardCharsets.UTF_8).length!=at||next.start!=at+a.length||next.text.getBytes(StandardCharsets.UTF_8).length>16384) throw new AssertionError("adjacency");
  long split=s.previewBoundary(1,"large.txt",v,at+2); if(split!=at)throw new AssertionError("boundary");
  System.out.println("PASS size="+size+" initial="+a.length+" previous="+prev.text.getBytes(StandardCharsets.UTF_8).length+" next="+next.text.getBytes(StandardCharsets.UTF_8).length+" markerOffset="+at+" heapMax="+Runtime.getRuntime().maxMemory());
 }
}
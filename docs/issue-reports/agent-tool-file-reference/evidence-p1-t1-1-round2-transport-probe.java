package com.et.agent;
import com.sun.net.httpserver.*; import java.net.*; import java.nio.charset.StandardCharsets; import java.util.*; import java.util.concurrent.atomic.AtomicInteger;
public class Probe {
 public static void main(String[] a) throws Exception { for(String mode: new String[]{"corrupt","disconnect"}) run(mode); }
 static void run(String mode) throws Exception {
  AtomicInteger received=new AtomicInteger(), turns=new AtomicInteger(); HttpServer s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  s.createContext("/api/internal/agent/files",x->{ x.getRequestBody().readAllBytes(); int n=received.incrementAndGet(); System.out.println(mode+" received POST "+n); if(mode.equals("corrupt")){ byte[] b="{invalid".getBytes(StandardCharsets.UTF_8); x.sendResponseHeaders(200,b.length); x.getResponseBody().write(b); } x.close(); }); s.start();
  try { AgentLibraryHttpGateway g=new AgentLibraryHttpGateway("http://127.0.0.1:"+s.getAddress().getPort(),"key"); AgentLoop l=new AgentLoop((h,d,c)->{int t=turns.getAndIncrement(); if(t>0) System.out.println(mode+" tool result "+t+": "+h.get(h.size()-1).content); if(t<2) return new AgentLoop.Turn(Collections.singletonList(new AgentLoop.ToolCall(""+t,"write_file","{\"path\":\"a.txt\",\"content\":\"text\",\"mode\":\"create\"}")),null); return new AgentLoop.Turn(Collections.emptyList(),null);},AgentToolRegistry.bind("7",g),4,30000,3000); l.run("test",v->{},()->false); System.out.println(mode+" total requests="+received.get()); } finally{s.stop(0);}
 }
}
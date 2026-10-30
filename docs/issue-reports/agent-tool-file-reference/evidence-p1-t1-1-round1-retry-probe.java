package com.et.agent;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public class RetryProbe {
 public static void main(String[] args) throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  ExecutorService executor=Executors.newCachedThreadPool(); server.setExecutor(executor); AtomicInteger writes=new AtomicInteger();
  server.createContext("/api/internal/agent/files", e->{e.getRequestBody().readAllBytes(); writes.incrementAndGet(); try { Thread.sleep(1000); } catch(InterruptedException ex){Thread.currentThread().interrupt();} e.close();}); server.start();
  try { AgentLibraryHttpGateway gateway=new AgentLibraryHttpGateway("http://127.0.0.1:"+server.getAddress().getPort(),"secret",Duration.ofMillis(300)); AtomicInteger turns=new AtomicInteger();
   AgentLoop loop=new AgentLoop((history,delta,cancelled)->{int n=turns.getAndIncrement(); if(n<2) { if(n==1) System.out.println("first result="+history.get(history.size()-1).content); return new AgentLoop.Turn(Collections.singletonList(new AgentLoop.ToolCall(""+(n+1),"write_file","{\"path\":\"a.txt\",\"content\":\"text\",\"mode\":\"create\"}")),null); } return new AgentLoop.Turn(Collections.emptyList(),null); },AgentToolRegistry.bind("7",gateway),4,60000,2000);
   loop.run("test",chunk->{},()->false); System.out.println("write requests="+writes.get());
  } finally { server.stop(0); executor.shutdownNow(); }
 }
}
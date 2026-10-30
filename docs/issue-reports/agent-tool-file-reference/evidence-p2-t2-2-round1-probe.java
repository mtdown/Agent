package com.et.cloud.controller;
import java.nio.file.*; import java.io.*; import java.nio.charset.StandardCharsets; import java.security.*; import java.util.*; import java.lang.reflect.*;
public class Probe {
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] args)throws Exception{
  Path root=Paths.get(args[0]); Path user=Files.createDirectories(root.resolve("1")); Files.createDirectories(root.resolve("2"));
  byte[] block=new byte[1048576]; Arrays.fill(block,(byte)'x'); byte[] zh="中".getBytes(StandardCharsets.UTF_8); System.arraycopy(zh,0,block,1048574,2);
  try(OutputStream out=Files.newOutputStream(user.resolve("large"))){ out.write(block); out.write(zh[2]); Arrays.fill(block,(byte)'x'); for(int i=1;i<49;i++)out.write(block); out.write(block,0,1048575); }
  AgentPersonalFileStore s=new AgentPersonalFileStore(root); MessageDigest actual=MessageDigest.getInstance("SHA-256"), expected=MessageDigest.getInstance("SHA-256");
  try(InputStream in=Files.newInputStream(user.resolve("large"))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)expected.update(b,0,n);}
  AgentPersonalFileStore.Cursor c=null; int pages=0; long total=0; do {var p=s.segment(1,"large",c,1048576); byte[] b=p.text.getBytes(StandardCharsets.UTF_8);check(b.length<=1048576,"limit");actual.update(b);total+=b.length;pages++;c=p.next;}while(c!=null);
  check(Arrays.equals(actual.digest(),expected.digest()),"digest"); System.out.println("heap="+Runtime.getRuntime().maxMemory()+" bytes="+total+" pages="+pages+" SHA256=match");
  Files.writeString(user.resolve("small"),"a中文aaa中文aa",StandardCharsets.UTF_8); Files.writeString(user.resolve("other"),"a中文aaa中文aa",StandardCharsets.UTF_8); Files.writeString(root.resolve("2/small"),"a中文aaa中文aa",StandardCharsets.UTF_8);
  var seg=s.segment(1,"small",null,4); try{s.segment(2,"small",seg.next,4);throw new AssertionError("user");}catch(IllegalArgumentException ok){} try{s.segment(1,"other",seg.next,4);throw new AssertionError("path");}catch(IllegalArgumentException ok){}
  Constructor<AgentPersonalFileStore.Cursor> ctor=AgentPersonalFileStore.Cursor.class.getDeclaredConstructor(long.class,String.class,String.class,long.class,String.class);ctor.setAccessible(true);var invalid=ctor.newInstance(1L,"small",seg.version,-1L,null);try{s.segment(1,"small",invalid,4);throw new AssertionError("position");}catch(IllegalArgumentException ok){}
  List<Long> offsets=new ArrayList<>(); c=null; int scanPages=0; do{var p=s.search(1,"small","中文",c,1,1);offsets.addAll(p.offsets);c=p.next;scanPages++;}while(c!=null);check(offsets.equals(List.of(1L,10L)),"offsets="+offsets);check(scanPages>=15,"scanPages="+scanPages);
  c=s.search(1,"small","中文",null,1,1).next; s.write(1,"small","changed","overwrite");try{s.search(1,"small","中文",c,1,1);throw new AssertionError("version");}catch(IllegalStateException ok){} System.out.println("isolation,path,invalid cursor,search positions="+offsets+", scanPages="+scanPages+", version conflict: PASS");
 }
}


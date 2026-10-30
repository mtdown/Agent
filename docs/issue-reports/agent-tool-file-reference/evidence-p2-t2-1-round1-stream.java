import com.et.cloud.controller.AgentPersonalFileStore;
import java.io.Reader;
import java.nio.file.*;
import java.util.Arrays;
public class StreamProbe {
 public static void main(String[] a) throws Exception {
  AgentPersonalFileStore s = new AgentPersonalFileStore(a[0]);
  Reader r = new Reader() { long remaining=50L*1024*1024; public int read(char[] c,int o,int n) { if(remaining==0)return -1; int k=(int)Math.min(n,remaining); Arrays.fill(c,o,o+k,'x'); remaining-=k; return k; } public void close() {} };
  int n=s.write(7,"stream",r,"create",null).bytes; if(n!=50*1024*1024 || Files.size(Path.of(a[0],"7","stream"))!=n) throw new AssertionError(); System.out.println("STREAM_BYTES="+n);
 }
}
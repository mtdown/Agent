import com.et.cloud.controller.AgentPersonalFileStore;
import java.io.StringReader;
public class Probe {
 public static void main(String[] a) {
  AgentPersonalFileStore s = new AgentPersonalFileStore(a[0]);
  if (a[1].equals("create")) { s.write(7,"a","A","create"); System.out.println("VERSION="+s.version(7,"a")); }
  if (a[1].equals("check")) { String v=s.version(7,"a"); System.out.println("VERSION="+v); if (!v.equals(a[2])) throw new AssertionError("version changed across JVM"); s.write(7,"a","B","overwrite"); s.write(7,"a","A","overwrite"); try { s.write(7,"a",new StringReader("C"),"overwrite",v); throw new AssertionError("stale version accepted"); } catch (IllegalStateException expected) { System.out.println("STALE_REJECTED"); } }
  if (a[1].equals("junction")) { try { s.write(7,"link/escape.txt","x","create"); System.out.println("JUNCTION_WRITE_ACCEPTED"); } catch (RuntimeException e) { System.out.println("JUNCTION_REJECTED="+e.getClass().getName()+":"+e.getMessage()); } }
 }
}
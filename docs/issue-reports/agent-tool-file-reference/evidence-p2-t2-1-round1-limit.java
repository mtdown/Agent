import com.et.cloud.controller.AgentPersonalFileStore;
import java.io.Reader;
import java.util.Arrays;
public class LimitProbe {
 static Reader data(long n) { return new Reader() { long left=n; public int read(char[] c,int o,int len) { if(left==0)return -1; int k=(int)Math.min(left,len); Arrays.fill(c,o,o+k,'z'); left-=k; return k; } public void close(){} }; }
 public static void main(String[] a) { AgentPersonalFileStore s=new AgentPersonalFileStore(a[0]); for(String mode:new String[]{"overwrite","append"}) { try { s.write(7,"stream",data(mode.equals("append")?1:52428801),mode,null); throw new AssertionError(mode+" accepted"); } catch(IllegalArgumentException ok) { System.out.println(mode+" rejected"); } } }
}
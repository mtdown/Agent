import com.et.agent.*;
import com.et.cloud.controller.AgentSkillDefinitionValidator;
import java.lang.reflect.Proxy;

// Local probe: real registry and Cloud validator, fake gateway and resolver; no network or database.
class ProbeTask12Round2 {
  public static void main(String[] args) {
    String[] definitions = {
      "{\"name\":\"a\",\"summary\":\"a\",\"when_to_use\":\"a\",\"when_not_to_use\":\"a\",\"parameters\":[],\"steps\":[{\"type\":\"code\",\"code_item_id\":\"1\",\"code_version\":1.0,\"entrypoint\":\"main\",\"args\":{}}],\"expected_results\":[\"ok\"],\"failure_policy\":\"stop\",\"examples\":[{\"input\":{},\"expected\":\"ok\"}]}",
      "{\"name\":\"a\",\"summary\":\"a\",\"when_to_use\":\"a\",\"when_not_to_use\":\"a\",\"parameters\":[{\"name\":\"n\",\"type\":\"number\",\"required\":true,\"min\":1.00000000000000001,\"max\":1}],\"steps\":[{\"type\":\"response\",\"template\":\"ok\"}],\"expected_results\":[\"ok\"],\"failure_policy\":\"stop\",\"examples\":[{\"input\":{},\"expected\":\"ok\"}]}"
    };
    for (int i=0; i<definitions.length; i++) {
      System.out.println("CASE " + (i+1) + " input=" + definitions[i]);
      AgentLibraryGateway gateway=(AgentLibraryGateway)Proxy.newProxyInstance(AgentLibraryGateway.class.getClassLoader(), new Class[]{AgentLibraryGateway.class}, (proxy, method, values) -> {
        if (!method.getName().equals("createSkill")) throw new AssertionError("Unexpected gateway method");
        String payload=(String) values[1]; System.out.println("gateway payload=" + payload);
        try { new AgentSkillDefinitionValidator().validate(payload,(id,version,entrypoint)->true); System.out.println("Cloud validator=ACCEPT"); }
        catch (IllegalArgumentException ex) { System.out.println("Cloud validator=REJECT: " + ex.getMessage()); }
        return "fake-id"; // Observation only: deliberately does not model HTTP failure or persistence.
      });
      System.out.println("Agent fake result=" + AgentToolRegistry.bind("7",gateway).get("create_skill").execute("{\"definition\":"+definitions[i]+"}"));
    }
  }
}

package com.et.agent;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class AgentControllerTest {
    private AgentController controller(AgentLoop.Model model) {
        return new AgentController(new AgentLoop(model, Collections.emptyMap(), 2, 10000, 1000), "test-secret");
    }

    @Test void rejectsUnauthenticatedCall() {
        assertThrows(ResponseStatusException.class, () -> controller((h, d, c) -> null).stream("bad", new AgentController.Ask("q")));
    }

    @Test void emitsDeltasAndExactlyOneDoneWithUnavailableUsage() throws Exception {
        AgentController controller = controller((h, d, c) -> {
            d.accept("a"); d.accept("b");
            return new AgentLoop.Turn(Collections.emptyList(), null);
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        controller.stream("test-secret", new AgentController.Ask("q")).writeTo(out);
        String frames = out.toString(StandardCharsets.UTF_8.name());
        assertEquals(2, frames.split("event: delta", -1).length - 1);
        assertEquals(1, frames.split("event: done", -1).length - 1);
        assertTrue(frames.contains("\"usage\":null"));
        assertTrue(frames.contains("\"firstAnswerMs\":"));
    }

    @Test void emitsOneError() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        controller((h, d, c) -> { throw new IllegalStateException("broken"); })
                .stream("test-secret", new AgentController.Ask("q")).writeTo(out);
        String frames = out.toString(StandardCharsets.UTF_8.name());
        assertEquals(1, frames.split("event: error", -1).length - 1);
        assertFalse(frames.contains("event: done"));
    }
}

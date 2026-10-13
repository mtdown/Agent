package com.et.agent;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiStreamModelTest {
    @Test void parsesTextFragmentedToolsAndUsage() {
        List<String> chunks = new ArrayList<>();
        OpenAiStreamModel.Parser parser = new OpenAiStreamModel.Parser(chunks::add);
        parser.accept("data: {\"choices\":[{\"delta\":{\"content\":\"A\"}}]}");
        parser.accept("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\",\"function\":{\"name\":\"look\",\"arguments\":\"{\\\"a\\\":\"}}]}}]}");
        parser.accept("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"name\":\"up\",\"arguments\":\"1}\"}}]},\"finish_reason\":\"tool_calls\"}]}");
        parser.accept("data: {\"choices\":[],\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":3}}");
        parser.accept("data: [DONE]");
        AgentLoop.Turn result = parser.finish();
        assertEquals(Collections.singletonList("A"), chunks);
        assertEquals("lookup", result.toolCalls.get(0).name);
        assertEquals("{\"a\":1}", result.toolCalls.get(0).arguments);
        assertEquals(7, result.usage.totalTokens);
    }

    @Test void rejectsMalformedEvent() {
        OpenAiStreamModel.Parser parser = new OpenAiStreamModel.Parser(text -> {});
        assertThrows(IllegalArgumentException.class, () -> parser.accept("data: {broken"));
    }
}

package com.et.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Bounded model/tool/result loop. A model turn may stream text before returning tool calls. */
public final class AgentLoop {
    private static final ObjectMapper JSON = new ObjectMapper();
    public interface Model {
        Turn stream(List<Message> history, Consumer<String> answerDelta, BooleanSupplier cancelled);
    }
    public interface Tool { String execute(String jsonArguments); }
    public static final class Message {
        public final String role, content, toolCallId, name;
        public final List<ToolCall> toolCalls;
        public Message(String role, String content, String toolCallId, String name) {
            this(role, content, toolCallId, name, Collections.emptyList());
        }
        public Message(String role, String content, String toolCallId, String name, List<ToolCall> toolCalls) {
            this.role = role; this.content = content; this.toolCallId = toolCallId; this.name = name; this.toolCalls = toolCalls;
        }
    }
    public static final class ToolCall {
        public final String id, name, arguments;
        public ToolCall(String id, String name, String arguments) {
            this.id = id; this.name = name; this.arguments = arguments;
        }
    }
    public static final class Usage {
        public final long promptTokens, completionTokens, totalTokens;
        public Usage(long promptTokens, long completionTokens) {
            this.promptTokens = promptTokens; this.completionTokens = completionTokens;
            this.totalTokens = promptTokens + completionTokens;
        }
    }
    public static final class Turn {
        public final List<ToolCall> toolCalls;
        public final Usage usage;
        public Turn(List<ToolCall> toolCalls, Usage usage) {
            this.toolCalls = toolCalls; this.usage = usage;
        }
    }
    public static final class Result {
        public final Usage usage;
        public final Long firstAnswerMs;
        public final long totalMs;
        public Result(Usage usage, Long firstAnswerMs, long totalMs) {
            this.usage = usage; this.firstAnswerMs = firstAnswerMs; this.totalMs = totalMs;
        }
    }
    public static final class Cancelled extends RuntimeException { }

    private final Model model;
    private final Map<String, Tool> tools;
    private final int maxTurns, maxToolResultChars;
    private final long timeoutMs;

    public AgentLoop(Model model, Map<String, Tool> tools, int maxTurns, long timeoutMs, int maxToolResultChars) {
        this.model = Objects.requireNonNull(model);
        this.tools = new HashMap<>(tools);
        this.maxTurns = maxTurns; this.timeoutMs = timeoutMs; this.maxToolResultChars = maxToolResultChars;
    }

    public Result run(String prompt, Consumer<String> delta, BooleanSupplier cancelled) {
        if (prompt == null || prompt.trim().isEmpty()) throw new IllegalArgumentException("Question is empty");
        long started = System.nanoTime();
        List<Message> history = new ArrayList<>();
        history.add(new Message("system", "You are an assistant. This preview has no knowledge-base tools. Do not claim a policy answer is grounded in documents.", null, null));
        history.add(new Message("user", prompt, null, null));
        final Long[] firstAnswerMs = {null};
        long input = 0, output = 0;
        boolean usageAvailable = true;
        int toolResultChars = 0;
        for (int turnNumber = 1; turnNumber <= maxTurns; turnNumber++) {
            check(started, cancelled);
            StringBuilder answer = new StringBuilder();
            Turn turn = model.stream(Collections.unmodifiableList(history), chunk -> {
                check(started, cancelled);
                if (chunk != null && !chunk.isEmpty()) {
                    if (firstAnswerMs[0] == null) firstAnswerMs[0] = elapsed(started);
                    answer.append(chunk);
                    delta.accept(chunk);
                }
            }, cancelled);
            check(started, cancelled);
            if (turn.usage == null) usageAvailable = false;
            else { input += turn.usage.promptTokens; output += turn.usage.completionTokens; }
            if (turn.toolCalls == null || turn.toolCalls.isEmpty())
                return new Result(usageAvailable ? new Usage(input, output) : null, firstAnswerMs[0], elapsed(started));
            if (turnNumber == maxTurns) throw new IllegalStateException("Agent turn limit reached");
            history.add(new Message("assistant", answer.toString(), null, null, turn.toolCalls));
            for (ToolCall call : turn.toolCalls) {
                if (call.id == null || call.id.isEmpty() || call.name == null || call.arguments == null)
                    throw new IllegalArgumentException("Malformed tool call");
                Tool tool = tools.get(call.name);
                if (tool == null) throw new IllegalArgumentException("Unknown tool: " + call.name);
                try {
                    if (!JSON.readTree(call.arguments).isObject()) throw new IllegalArgumentException("Tool arguments must be an object");
                } catch (java.io.IOException e) {
                    throw new IllegalArgumentException("Malformed tool arguments", e);
                }
                String result = tool.execute(call.arguments);
                if (result == null) throw new IllegalStateException("Tool returned no result");
                toolResultChars += result.length();
                if (toolResultChars > maxToolResultChars) throw new IllegalStateException("Tool result limit reached");
                history.add(new Message("tool", result, call.id, call.name));
            }
        }
        throw new IllegalStateException("Agent turn limit reached");
    }

    private void check(long started, BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new Cancelled();
        if (elapsed(started) > timeoutMs) throw new IllegalStateException("Agent request timed out");
    }
    private static long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
}

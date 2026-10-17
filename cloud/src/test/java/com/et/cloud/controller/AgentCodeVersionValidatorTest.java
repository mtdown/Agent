package com.et.cloud.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentCodeVersionValidatorTest {
    private final AgentCodeVersionValidator validator = new AgentCodeVersionValidator();
    private static final String INPUT = "{\"type\":\"object\",\"properties\":{\"count\":{\"type\":\"integer\"}},\"required\":[\"count\"]}";
    private static final String OUTPUT = "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}}}";

    @Test
    void acceptsExplicitPythonAndJavaEntrypoints() {
        assertDoesNotThrow(() -> validator.validate("PYTHON", "run", "def run(inputs, resources):\n    return {'ok': True}", INPUT, OUTPUT));
        assertDoesNotThrow(() -> validator.validate("JAVA", "Runner", "class Runner implements SkillEntry { }", INPUT, OUTPUT));
    }

    @Test
    void rejectsUnsupportedLanguageOrEntrypoint() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("SHELL", "run", "echo hi", INPUT, OUTPUT));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("PYTHON", "../../run", "def run(): pass", INPUT, OUTPUT));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("PYTHON", "run", "", INPUT, OUTPUT));
    }

    @Test
    void rejectsMalformedOrUnboundedSchemas() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("PYTHON", "run", "pass", "{}", OUTPUT));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("PYTHON", "run", "pass",
                INPUT.replace("\"required\":[\"count\"]", "\"required\":[\"missing\"]"), OUTPUT));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("PYTHON", "run", "pass", INPUT,
                "{\"type\":\"array\"}"));
    }
}

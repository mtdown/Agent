package com.et.cloud.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates the versioned, executable part of a Skill before it is saved or enabled. */
@Component
public final class AgentSkillDefinitionValidator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]{0,79}");
    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");

    @FunctionalInterface
    public interface CodeVersionResolver {
        boolean exists(String codeId, int version, String entrypoint);
    }

    public JsonNode validate(String definitionJson, CodeVersionResolver codeVersions) {
        if (definitionJson == null || definitionJson.length() > 128_000 || codeVersions == null)
            throw invalid("Invalid Skill definition");
        final JsonNode root;
        try { root = JSON.readTree(definitionJson); }
        catch (JsonProcessingException e) { throw invalid("Invalid Skill JSON"); }
        object(root, "Skill");
        fields(root, "", "name", "summary", "when_to_use", "when_not_to_use", "parameters", "steps",
                "expected_results", "failure_policy", "examples");
        text(root, "name", 1, 160);
        text(root, "summary", 1, 500);
        text(root, "when_to_use", 1, 1000);
        text(root, "when_not_to_use", 1, 1000);
        String policy = text(root, "failure_policy", 1, 32);
        if (!policy.equals("stop") && !policy.equals("fallback")) throw invalid("Invalid failure policy");

        JsonNode parameters = array(root, "parameters", 0, 50);
        Set<String> names = new HashSet<>();
        for (int index = 0; index < parameters.size(); index++) {
            JsonNode parameter = parameters.get(index);
            object(parameter, "parameter");
            fields(parameter, "parameters[" + index + "]", "name", "type", "required", "min", "max");
            String name = text(parameter, "name", 1, 80);
            if (!IDENTIFIER.matcher(name).matches() || !names.add(name)) throw invalid("Invalid or duplicate parameter name");
            String type = text(parameter, "type", 1, 20);
            if (!Arrays.asList("string", "integer", "number", "boolean", "array", "object").contains(type))
                throw invalid("Invalid parameter type");
            if (!parameter.has("required") || !parameter.get("required").isBoolean())
                throw invalid("Parameter required must be boolean");
            if (parameter.has("min") || parameter.has("max")) {
                if (!Arrays.asList("string", "integer", "number", "array").contains(type))
                    throw invalid("Bounds are not supported for this type");
                JsonNode min = parameter.get("min");
                JsonNode max = parameter.get("max");
                if ((min != null && !min.isNumber()) || (max != null && !max.isNumber()))
                    throw invalid("Parameter bounds must be numbers");
                if (min != null && max != null && min.decimalValue().compareTo(max.decimalValue()) > 0)
                    throw invalid("Parameter min exceeds max");
            }
        }

        JsonNode steps = array(root, "steps", 1, 30);
        for (JsonNode step : steps) {
            object(step, "step");
            String type = text(step, "type", 1, 20);
            switch (type) {
                case "code":
                    fields(step, "step", "type", "code_item_id", "code_version", "entrypoint", "args");
                    String id = text(step, "code_item_id", 1, 19);
                    if (!DECIMAL_ID.matcher(id).matches() || new BigInteger(id).compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)
                        throw invalid("Code item ID must be a decimal string");
                    JsonNode version = step.get("code_version");
                    if (version == null || !version.isInt() || version.intValue() < 1)
                        throw invalid("A fixed positive code version is required");
                    String entrypoint = text(step, "entrypoint", 1, 80);
                    if (!IDENTIFIER.matcher(entrypoint).matches()) throw invalid("Invalid code entrypoint");
                    object(step.get("args"), "code arguments");
                    if (!codeVersions.exists(id, version.intValue(), entrypoint))
                        throw invalid("Code version or entrypoint does not exist");
                    break;
                case "tool":
                    fields(step, "step", "type", "tool", "args");
                    if (!Set.of("get_skill", "create_skill", "create_code", "write_file", "read_file",
                            "write_wiki_document").contains(text(step, "tool", 1, 80)))
                        throw invalid("Invalid tool name");
                    object(step.get("args"), "tool arguments");
                    break;
                case "response":
                    fields(step, "step", "type", "template");
                    text(step, "template", 1, 4000);
                    break;
                default: throw invalid("Unknown step type");
            }
        }

        for (JsonNode expected : array(root, "expected_results", 1, 30))
            if (!expected.isTextual() || expected.textValue().trim().isEmpty())
                throw invalid("Expected result must be text");
        for (JsonNode example : array(root, "examples", 1, 30)) {
            object(example, "example");
            fields(example, "example", "input", "expected");
            object(example.get("input"), "example input");
            if (!example.has("expected") || example.get("expected").isNull())
                throw invalid("Example expected result is required");
        }
        return root;
    }

    private static void fields(JsonNode node, String path, String... allowed) {
        Set<String> names = new HashSet<>(Arrays.asList(allowed));
        Iterator<String> actual = node.fieldNames();
        while (actual.hasNext()) {
            String field = actual.next();
            if (!names.contains(field)) throw invalid("Unknown field: " + (path.isEmpty() ? field : path + "." + field));
        }
    }

    private static void object(JsonNode node, String label) {
        if (node == null || !node.isObject()) throw invalid(label + " must be an object");
    }

    private static JsonNode array(JsonNode parent, String name, int min, int max) {
        JsonNode result = parent.get(name);
        if (result == null || !result.isArray() || result.size() < min || result.size() > max)
            throw invalid(name + " must be an array with " + min + " to " + max + " entries");
        return result;
    }

    private static String text(JsonNode parent, String name, int min, int max) {
        JsonNode result = parent.get(name);
        if (result == null || !result.isTextual()) throw invalid(name + " must be text");
        String value = result.textValue().trim();
        if (value.length() < min || value.length() > max) throw invalid(name + " length is invalid");
        return value;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}

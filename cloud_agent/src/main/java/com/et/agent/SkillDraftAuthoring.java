package com.et.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Built-in Skill authoring guide and the structured tool contract used to fill it. */
final class SkillDraftAuthoring {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final String[] FIELDS = {"name", "summary", "when_to_use", "when_not_to_use",
            "parameters", "steps", "expected_results", "failure_policy", "examples"};
    static final String INSTRUCTIONS = "Built-in Skill authoring: when the user requests a Skill or a completed task is repeatable, " +
            "infer the structured fields from the conversation. Ask only for essential missing facts; do not print a blank template. " +
            "Fill name, summary, use and exclusion conditions, typed parameters, controlled steps, observable expected results, " +
            "failure policy and examples before calling create_skill. Parameter entries allow only name, type, required, min and max. " +
            "A response step needs type=response and template. Report the Skill as saved and enabled only after create_skill returns an ID. " +
            "If a field error is returned, correct that field and retry; never claim an unsuccessful save succeeded.";

    private SkillDraftAuthoring() { }

    static ObjectNode toolParameters() {
        ObjectNode outer = object();
        outer.set("properties", JSON.createObjectNode().set("definition", definitionSchema()));
        outer.putArray("required").add("definition");
        return outer;
    }

    private static ObjectNode definitionSchema() {
        ObjectNode definition = object();
        ObjectNode props = definition.putObject("properties");
        for (String field : new String[]{"name", "summary", "when_to_use", "when_not_to_use"})
            props.set(field, string());
        ObjectNode parameter = object();
        ObjectNode parameterProps = parameter.putObject("properties");
        parameterProps.set("name", string());
        parameterProps.set("type", enumString("string", "integer", "number", "boolean", "array", "object"));
        parameterProps.putObject("required").put("type", "boolean");
        parameterProps.putObject("min").put("type", "number");
        parameterProps.putObject("max").put("type", "number");
        parameter.putArray("required").add("name").add("type").add("required");
        props.set("parameters", array(parameter));
        ObjectNode step = object();
        ObjectNode stepProps = step.putObject("properties");
        stepProps.set("type", enumString("response", "tool", "code"));
        stepProps.set("template", string());
        stepProps.set("tool", string());
        stepProps.set("args", freeObject());
        stepProps.set("code_item_id", string());
        stepProps.putObject("code_version").put("type", "integer");
        stepProps.set("entrypoint", string());
        step.putArray("required").add("type");
        props.set("steps", array(step));
        props.set("expected_results", array(string()));
        props.set("failure_policy", enumString("stop", "fallback"));
        ObjectNode example = object();
        ObjectNode exampleProps = example.putObject("properties");
        exampleProps.set("input", freeObject());
        exampleProps.putObject("expected");
        example.putArray("required").add("input").add("expected");
        props.set("examples", array(example));
        ArrayNode required = definition.putArray("required");
        for (String field : FIELDS) required.add(field);
        return definition;
    }

    static String assemble(String rawArguments) {
        final JsonNode args;
        try { args = JSON.readTree(rawArguments); }
        catch (IOException e) { throw new IllegalArgumentException("Invalid Skill tool arguments", e); }
        if (args == null || !args.isObject()) throw new IllegalArgumentException("Skill tool arguments must be an object");
        checkFields(args, "", "definition");
        JsonNode definition = args.path("definition");
        if (!definition.isObject()) throw new IllegalArgumentException("definition must be an object");
        checkFields(definition, "definition", FIELDS);
        checkArrayObjects(definition.path("parameters"), "definition.parameters", "name", "type", "required", "min", "max");
        checkArrayObjects(definition.path("steps"), "definition.steps", "type", "template", "tool", "args",
                "code_item_id", "code_version", "entrypoint");
        checkArrayObjects(definition.path("examples"), "definition.examples", "input", "expected");
        for (JsonNode step : definition.path("steps")) {
            if (!"code".equals(step.path("type").asText()) || !step.has("code_version")) continue;
            JsonNode version = step.path("code_version");
            try {
                if (!version.isNumber()) throw new ArithmeticException();
                int exact = version.decimalValue().intValueExact();
                if (exact < 1) throw new ArithmeticException();
                ((ObjectNode) step).put("code_version", exact);
            } catch (ArithmeticException invalid) {
                throw new IllegalArgumentException("Invalid code version");
            }
        }
        ObjectNode assembled = JSON.createObjectNode();
        for (String field : FIELDS) if (definition.has(field)) assembled.set(field, definition.get(field));
        return assembled.toString();
    }

    private static void checkArrayObjects(JsonNode items, String path, String... allowed) {
        if (!items.isArray()) return;
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            if (!item.isObject()) throw new IllegalArgumentException(path + "[" + i + "] must be an object");
            checkFields(item, path + "[" + i + "]", allowed);
        }
    }

    private static void checkFields(JsonNode node, String path, String... allowed) {
        Set<String> names = new HashSet<>(Arrays.asList(allowed));
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!names.contains(field)) throw new IllegalArgumentException("Unknown field: " +
                    (path.isEmpty() ? field : path + "." + field));
        }
    }

    private static ObjectNode object() { return JSON.createObjectNode().put("type", "object").put("additionalProperties", false); }
    private static ObjectNode freeObject() { return JSON.createObjectNode().put("type", "object"); }
    private static ObjectNode string() { return JSON.createObjectNode().put("type", "string"); }
    private static ObjectNode array(JsonNode items) { ObjectNode result = JSON.createObjectNode().put("type", "array"); result.set("items", items); return result; }
    private static ObjectNode enumString(String... values) {
        ObjectNode result = string();
        for (String value : values) result.withArray("enum").add(value);
        return result;
    }
}

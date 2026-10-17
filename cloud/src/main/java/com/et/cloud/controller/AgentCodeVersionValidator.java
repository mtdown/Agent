package com.et.cloud.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

/** Small, explicit input/output contract supported by the first Python and Java runners. */
@Component
public final class AgentCodeVersionValidator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ENTRYPOINT = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]{0,79}");
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
            "string", "integer", "number", "boolean", "array", "object"));

    public void validate(String language, String entrypoint, String sourceCode,
                         String inputSchemaJson, String outputSchemaJson) {
        if (!"PYTHON".equals(language) && !"JAVA".equals(language))
            throw invalid("Unsupported code language");
        if (entrypoint == null || !ENTRYPOINT.matcher(entrypoint).matches())
            throw invalid("Invalid code entrypoint");
        if (sourceCode == null || sourceCode.trim().isEmpty() || sourceCode.length() > 200_000)
            throw invalid("Source code is empty or too large");
        schema(inputSchemaJson, "input");
        schema(outputSchemaJson, "output");
    }

    private static void schema(String source, String label) {
        if (source == null || source.length() > 20_000) throw invalid(label + " schema is missing or too large");
        final JsonNode schema;
        try { schema = JSON.readTree(source); }
        catch (JsonProcessingException e) { throw invalid(label + " schema is not JSON"); }
        if (schema == null || !schema.isObject() || !"object".equals(schema.path("type").asText()))
            throw invalid(label + " schema must describe an object");
        fields(schema, "type", "properties", "required", "additionalProperties");
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject() || properties.size() > 100)
            throw invalid(label + " schema properties must be an object");
        Iterator<String> propertyNames = properties.fieldNames();
        while (propertyNames.hasNext()) {
            String name = propertyNames.next();
            JsonNode property = properties.get(name);
            if (!ENTRYPOINT.matcher(name).matches() || !property.isObject())
                throw invalid("Invalid schema property");
            fields(property, "type", "description");
            if (!TYPES.contains(property.path("type").asText()))
                throw invalid("Unsupported schema property type");
            if (property.has("description") && !property.get("description").isTextual())
                throw invalid("Schema description must be text");
        }
        JsonNode required = schema.get("required");
        if (required != null) {
            if (!required.isArray()) throw invalid("Schema required must be an array");
            Set<String> seen = new HashSet<>();
            for (JsonNode name : required) {
                if (!name.isTextual() || !properties.has(name.textValue()) || !seen.add(name.textValue()))
                    throw invalid("Schema required refers to an unknown or duplicate property");
            }
        }
        if (schema.has("additionalProperties") && !schema.get("additionalProperties").isBoolean())
            throw invalid("Schema additionalProperties must be boolean");
    }

    private static void fields(JsonNode node, String... allowed) {
        Set<String> names = new HashSet<>(Arrays.asList(allowed));
        Iterator<String> actual = node.fieldNames();
        while (actual.hasNext()) if (!names.contains(actual.next())) throw invalid("Unknown schema field");
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}

package com.et.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Internal model tool schemas and handlers, kept in one registration map. */
public final class AgentToolRegistry {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    @FunctionalInterface
    private interface Binder { AgentLoop.Tool bind(String userId, AgentLibraryGateway gateway); }

    enum Domain { SKILL, CODE, FILE, WIKI, WEB }
    enum Access { READ, WRITE }

    private static final class Spec {
        final String name, description;
        final ObjectNode parameters;
        final Binder binder;
        final Domain domain;
        final Access access;
        Spec(String name, String description, ObjectNode parameters, Binder binder, Domain domain, Access access) {
            this.name = name; this.description = description; this.parameters = parameters; this.binder = binder;
            this.domain = java.util.Objects.requireNonNull(domain); this.access = java.util.Objects.requireNonNull(access);
        }
    }

    static {
        register(Domain.SKILL, Access.READ, "get_skill", "Read a currently enabled Skill by exact ID and version.",
                fields(new String[]{"id", "version"}, new String[]{"string", "integer"}),
                (user, gateway) -> raw -> {
                    JsonNode args = arguments(raw);
                    if (!args.path("version").canConvertToInt())
                        throw new IllegalArgumentException("Invalid Skill version");
                    return gateway.getSkill(user, requiredText(args, "id"), args.path("version").asInt(0));
                });
        register(Domain.SKILL, Access.WRITE, "create_skill", "Save and immediately enable a structured Skill as version 1 in the current user's library.",
                SkillDraftAuthoring.toolParameters(),
                (user, gateway) -> raw -> result("id", gateway.createSkill(user, SkillDraftAuthoring.assemble(raw)),
                        "state", "ACTIVE", "version", 1));
        ObjectNode code = fields(new String[]{"name", "summary", "language", "entrypoint", "sourceCode",
                "inputSchema", "outputSchema"},
                new String[]{"string", "string", "string", "string", "string", "object", "object"});
        register(Domain.CODE, Access.WRITE, "create_code", "Save and publish Python or Java source as immutable version 1, initially UNVERIFIED. The version can be bound to a Skill without sample validation.", code,
                (user, gateway) -> raw -> {
                    JsonNode args = arguments(raw);
                    ObjectNode body = JSON.createObjectNode();
                    for (String field : new String[]{"name", "summary", "language", "entrypoint", "sourceCode"})
                        body.put(field, requiredText(args, field));
                    JsonNode input = args.path("inputSchema"), output = args.path("outputSchema");
                    if (!input.isObject() || !output.isObject())
                        throw new IllegalArgumentException("Code schemas must be objects");
                    body.put("inputSchemaJson", input.toString());
                    body.put("outputSchemaJson", output.toString());
                    return result("id", gateway.createCode(user, body.toString()), "validationState", "UNVERIFIED",
                            "version", 1, "published", true);
                });
        ObjectNode file = fields(new String[]{"path", "content", "mode"},
                new String[]{"string", "string", "string"});
        ((ObjectNode) file.path("properties").path("mode")).putArray("enum")
                .add("create").add("overwrite").add("append");
        register(Domain.FILE, Access.WRITE, "write_file", "Write UTF-8 text to the current user's persistent personal files. " +
                        "Use mode=create for new files, overwrite or append only when requested.", file,
                (user, gateway) -> raw -> {
                    JsonNode args = arguments(raw);
                    return gateway.writeFile(user, requiredText(args, "path"), requiredText(args, "content"),
                            requiredText(args, "mode"));
                });
        register(Domain.FILE, Access.READ, "read_file", "Read a UTF-8 file from the current user's personal files.",
                fields(new String[]{"path"}, new String[]{"string"}),
                (user, gateway) -> raw -> gateway.readFile(user, requiredText(arguments(raw), "path")));
        ObjectNode segment = fields(new String[]{"path"}, new String[]{"string"});
        segment.with("properties").putObject("cursor").put("type", "string");
        register(Domain.FILE, Access.READ, "read_file_segment", "Read a bounded UTF-8 segment; pass next as cursor to continue.", segment,
                (user, gateway) -> raw -> { JsonNode args = arguments(raw); return gateway.readFileSegment(user,
                        requiredText(args, "path"), optionalText(args, "cursor")); });
        ObjectNode search = fields(new String[]{"path", "query"}, new String[]{"string", "string"});
        search.with("properties").putObject("cursor").put("type", "string");
        register(Domain.FILE, Access.READ, "search_file", "Search a bounded portion of a personal file; pass next as cursor to continue.", search,
                (user, gateway) -> raw -> { JsonNode args = arguments(raw); return gateway.searchFile(user,
                        requiredText(args, "path"), requiredText(args, "query"), optionalText(args, "cursor")); });
        register(Domain.WIKI, Access.WRITE, "write_wiki_document", "Compatibility write: create a new task-and-date folder, then a Markdown document in it. " +
                        "Requires an exact writable space ID, title and content. Returns real folderId and documentId.",
                fields(new String[]{"spaceId", "title", "content"}, new String[]{"string", "string", "string"}),
                (user, gateway) -> raw -> {
                    JsonNode args = arguments(raw);
                    return gateway.writeWikiDocument(user, requiredText(args, "spaceId"),
                            requiredText(args, "title"), requiredText(args, "content"));
                });
        register(Domain.WIKI, Access.WRITE, "create_wiki_folder", "Create one new task-and-date root folder in a writable Wiki space. " +
                        "Use the real spaceId returned by list_writable_wiki_locations; returns folderId as a string.",
                fields(new String[]{"spaceId", "taskName"}, new String[]{"string", "string"}),
                (user, gateway) -> raw -> {
                    JsonNode args = arguments(raw);
                    return gateway.createWikiFolder(user, requiredText(args, "spaceId"),
                            requiredText(args, "taskName"));
                });
        ObjectNode createDocument = fields(new String[]{"spaceId", "folderId", "content"},
                new String[]{"string", "string", "string"});
        createDocument.with("properties").putObject("title").put("type", "string");
        register(Domain.WIKI, Access.WRITE, "create_wiki_document", "Create a Markdown document inside an active folder of a writable Wiki space. " +
                        "Use the folderId from create_wiki_folder. An omitted or blank title is derived from content. " +
                        "Returns documentId, folderId and spaceId as strings; report success only with a documentId.",
                createDocument, (user, gateway) -> raw -> {
                    JsonNode args = arguments(raw);
                    JsonNode title = args.path("title");
                    if (!title.isMissingNode() && !title.isTextual())
                        throw new IllegalArgumentException("Invalid title");
                    return gateway.createWikiDocument(user, requiredText(args, "spaceId"),
                            requiredText(args, "folderId"), title.isMissingNode() ? null : title.asText(),
                            requiredText(args, "content"));
                });
        ObjectNode locations = JSON.createObjectNode().put("type", "object").put("additionalProperties", false);
        ObjectNode locationFields = locations.putObject("properties");
        locationFields.putObject("spaceId").put("type", "string");
        locationFields.putObject("cursor").put("type", "string");
        locationFields.putObject("limit").put("type", "integer");
        locations.putArray("required");
        register(Domain.WIKI, Access.READ, "list_writable_wiki_locations", "List the current user's writable public, personal and team spaces. " +
                        "With spaceId, list up to limit active folders; pass nextCursor as cursor for the next page. " +
                        "IDs are for internal tool use only and must never appear in user-facing text.",
                locations, (user, gateway) -> raw -> {
                    JsonNode args = arguments(raw);
                    String spaceId = optionalText(args, "spaceId");
                    String cursor = optionalText(args, "cursor");
                    JsonNode limitNode = args.path("limit");
                    if (!limitNode.isMissingNode() && !limitNode.canConvertToInt())
                        throw new IllegalArgumentException("Invalid limit");
                    Integer limit = limitNode.isMissingNode() ? null : limitNode.asInt();
                    return gateway.listWritableWikiLocations(user, spaceId, cursor, limit);
                });
        ObjectNode planDestination = fields(new String[]{"destinationType"}, new String[]{"string"});
        ((ObjectNode) planDestination.path("properties").path("destinationType")).putArray("enum")
                .add("unspecified").add("personal").add("public").add("team");
        planDestination.with("properties").putObject("teamName").put("type", "string");
        register(Domain.WIKI, Access.READ, "plan_wiki_destination", "Resolve a Wiki save destination after listing writable spaces. " +
                        "Use destinationType=unspecified when the user gave no location; this requests clickable choices " +
                        "and stops before any write. Use teamName for a named team. Never show returned IDs to the user.",
                planDestination, (user, gateway) -> raw -> AgentWikiDestinationPlanner.plan(user, gateway, raw));
    }

    private AgentToolRegistry() { }

    private static void register(Domain domain, Access access, String name, String description, ObjectNode parameters, Binder binder) {
        if (SPECS.putIfAbsent(name, new Spec(name, description, parameters, binder, domain, access)) != null)
            throw new IllegalStateException("Duplicate Agent tool: " + name);
    }

    static Domain domain(String name) { return isWeb(name) ? Domain.WEB : requireSpec(name).domain; }
    static Access access(String name) { return isWeb(name) ? Access.READ : requireSpec(name).access; }
    private static Spec requireSpec(String name) {
        Spec spec = SPECS.get(name);
        if (spec == null) throw new IllegalArgumentException("Unknown Agent tool");
        return spec;
    }

    public static Set<String> names() { return Collections.unmodifiableSet(SPECS.keySet()); }

    public static Map<String, AgentLoop.Tool> bind(String userId, AgentLibraryGateway gateway) {
        Map<String, AgentLoop.Tool> tools = new LinkedHashMap<>();
        for (Spec spec : SPECS.values()) tools.put(spec.name, validated(spec, spec.binder.bind(userId, gateway)));
        return tools;
    }

    private static AgentLoop.Tool validated(Spec spec, AgentLoop.Tool delegate) {
        return raw -> {
            JsonNode value = arguments(raw);
            validate(spec.parameters, value);
            return delegate.execute(raw);
        };
    }

    private static void validate(JsonNode schema, JsonNode value) {
        String type = schema.path("type").asText("");
        boolean valid;
        switch (type) {
            case "object": valid = value.isObject(); break;
            case "array": valid = value.isArray(); break;
            case "string": valid = value.isTextual(); break;
            case "integer": valid = value.isNumber() && value.decimalValue().stripTrailingZeros().scale() <= 0; break;
            case "number": valid = value.isNumber(); break;
            case "boolean": valid = value.isBoolean(); break;
            case "null": valid = value.isNull(); break;
            case "": valid = true; break;
            default: throw new IllegalStateException("Unsupported tool schema type");
        }
        if (!valid) throw new IllegalArgumentException("Invalid tool parameter type");
        JsonNode choices = schema.path("enum");
        if (choices.isArray()) {
            boolean found = false;
            for (JsonNode choice : choices) if (choice.equals(value)) { found = true; break; }
            if (!found) throw new IllegalArgumentException("Invalid tool parameter value");
        }
        if (value.isObject()) {
            for (JsonNode required : schema.path("required"))
                if (!value.has(required.asText())) throw new IllegalArgumentException("Required tool parameter is missing");
            JsonNode properties = schema.path("properties");
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode child = properties.get(field.getKey());
                if (child != null) validate(child, field.getValue());
                else if (schema.has("additionalProperties") && schema.path("additionalProperties").isBoolean()
                        && !schema.path("additionalProperties").asBoolean())
                    throw new IllegalArgumentException("Unexpected tool parameter");
            }
        } else if (value.isArray() && schema.has("items")) {
            for (JsonNode item : value) validate(schema.path("items"), item);
        }
    }

    /** Keep a user-selected destination authoritative even if the model supplies another ID. */
    public static Map<String, AgentLoop.Tool> bind(String userId, AgentLibraryGateway gateway,
                                                    String selectedSpaceId) {
        Map<String, AgentLoop.Tool> tools = bind(userId, gateway);
        if (selectedSpaceId == null) return tools;
        if (!selectedSpaceId.matches("[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("Invalid selected Wiki space");
        for (String name : new String[]{"create_wiki_folder", "create_wiki_document", "write_wiki_document"}) {
            AgentLoop.Tool delegate = tools.get(name);
            tools.put(name, raw -> {
                if (!selectedSpaceId.equals(requiredText(arguments(raw), "spaceId")))
                    throw new IllegalArgumentException("Selected Wiki destination differs from tool target");
                return delegate.execute(raw);
            });
        }
        tools.put("plan_wiki_destination", validated(SPECS.get("plan_wiki_destination"), raw -> result("choiceRequired", false,
                "spaceId", selectedSpaceId)));
        return tools;
    }

    private static boolean isWeb(String name) { return "web_search".equals(name) || "web_fetch".equals(name); }

    public static ObjectNode declaration(String name) {
        if (isWeb(name)) {
            boolean search = "web_search".equals(name);
            ObjectNode tool = JSON.createObjectNode().put("type", "function");
            ObjectNode function = tool.putObject("function");
            function.put("name", name);
            function.put("description", search ? "Search the web when current information is needed." : "Fetch a webpage when summaries are insufficient or the user supplies a URL.");
            function.set("parameters", fields(new String[]{search ? "query" : "url"}, new String[]{"string"}));
            return tool;
        }
        Spec spec = SPECS.get(name);
        if (spec == null) throw new IllegalArgumentException("Unknown Agent tool: " + name);
        ObjectNode tool = JSON.createObjectNode().put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", spec.name);
        function.put("description", spec.description);
        function.set("parameters", spec.parameters.deepCopy());
        return tool;
    }

    private static ObjectNode fields(String[] names, String[] types) {
        ObjectNode root = JSON.createObjectNode().put("type", "object").put("additionalProperties", false);
        ObjectNode properties = root.putObject("properties");
        for (int i = 0; i < names.length; i++) properties.putObject(names[i]).put("type", types[i]);
        for (String name : names) root.withArray("required").add(name);
        return root;
    }

    private static JsonNode arguments(String raw) {
        try {
            JsonNode args = JSON.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw);
            if (args == null || !args.isObject()) throw new IllegalArgumentException("Tool arguments must be an object");
            return args;
        } catch (IOException e) { throw new IllegalArgumentException("Invalid tool arguments", e); }
    }

    private static String requiredText(JsonNode args, String name) {
        JsonNode value = args.path(name);
        if (!value.isTextual() || value.asText().trim().isEmpty())
            throw new IllegalArgumentException("Missing " + name);
        return value.asText();
    }

    private static String optionalText(JsonNode args, String name) {
        JsonNode value = args.path(name);
        if (value.isMissingNode()) return null;
        if (!value.isTextual() || value.asText().trim().isEmpty())
            throw new IllegalArgumentException("Invalid " + name);
        return value.asText();
    }

    private static String result(Object... fields) {
        ObjectNode node = JSON.createObjectNode();
        for (int i = 0; i < fields.length; i += 2) {
            Object value = fields[i + 1];
            if (value instanceof Integer) node.put((String) fields[i], (Integer) value);
            else if (value instanceof Boolean) node.put((String) fields[i], (Boolean) value);
            else node.put((String) fields[i], String.valueOf(value));
        }
        return node.toString();
    }
}

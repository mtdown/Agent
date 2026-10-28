package com.et.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/** Resolves a model-described destination against Cloud's current writable directory. */
final class AgentWikiDestinationPlanner {
    private static final ObjectMapper JSON = new ObjectMapper();

    private AgentWikiDestinationPlanner() { }

    static String plan(String userId, AgentLibraryGateway gateway, String rawArguments) {
        final JsonNode arguments;
        final JsonNode directory;
        try {
            arguments = JSON.readTree(rawArguments);
            directory = JSON.readTree(gateway.listWritableWikiLocations(userId, null, null, null));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid destination data", e);
        }
        if (arguments == null || !arguments.isObject() || !directory.path("spaces").isArray())
            throw new IllegalArgumentException("Invalid destination data");
        String type = arguments.path("destinationType").asText("");
        if (!type.equals("unspecified") && !type.equals("personal") && !type.equals("public") && !type.equals("team"))
            throw new IllegalArgumentException("Invalid destination type");
        String teamName = arguments.path("teamName").asText("").trim();
        if (type.equals("team") && teamName.isEmpty()) throw new IllegalArgumentException("Missing team name");
        ArrayNode matches = JSON.createArrayNode();
        for (JsonNode space : directory.path("spaces")) {
            String actualType = space.path("type").asText();
            String id = space.path("id").asText();
            if (!id.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid writable space");
            if (type.equals("unspecified") || (type.equals(actualType) &&
                    (!type.equals("team") || space.path("name").asText("").trim().equals(teamName))))
                matches.add(space);
        }
        if (matches.size() == 0) throw new IllegalArgumentException("No writable destination matched");
        ObjectNode result = JSON.createObjectNode();
        boolean needsChoice = type.equals("unspecified") || matches.size() > 1;
        result.put("choiceRequired", needsChoice);
        if (needsChoice) {
            result.set("candidates", matches);
            if (type.equals("unspecified"))
                result.put("recommendedSpaceId", directory.path("defaultPersonalSpaceId").asText(""));
        } else {
            result.put("spaceId", matches.get(0).path("id").asText());
        }
        return result.toString();
    }
}

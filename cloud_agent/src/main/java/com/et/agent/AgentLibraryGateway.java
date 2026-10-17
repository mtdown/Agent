package com.et.agent;

/** Cloud-backed library operations performed for the authenticated chat user. */
public interface AgentLibraryGateway {
    String getSkill(String userId, String id, int version);
    String createSkill(String userId, String definition);
    String createCode(String userId, String definition);
}

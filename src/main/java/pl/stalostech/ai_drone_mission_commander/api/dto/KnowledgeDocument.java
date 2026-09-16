package pl.stalostech.ai_drone_mission_commander.api.dto;

import java.util.Map;

public record KnowledgeDocument(String id, String text, Map<String, Object> metadata, Double score) {
    public KnowledgeDocument { metadata = Map.copyOf(metadata); }
}

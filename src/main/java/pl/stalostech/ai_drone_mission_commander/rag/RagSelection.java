package pl.stalostech.ai_drone_mission_commander.rag;

public record RagSelection(String query, int topK, double threshold, KnowledgeType type, KnowledgeTopic topic) {
    public RagSelection { KnowledgeSearchService.validateSearch(query, topK, threshold); }
}

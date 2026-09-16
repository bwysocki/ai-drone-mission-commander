package pl.stalostech.ai_drone_mission_commander.rag.exception;

public class KnowledgeIndexNotReadyException extends RuntimeException {
    public KnowledgeIndexNotReadyException() {
        super("Build the knowledge index with POST /api/knowledge/index before searching.");
    }
}

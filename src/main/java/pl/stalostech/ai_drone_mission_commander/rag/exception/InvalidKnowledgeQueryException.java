package pl.stalostech.ai_drone_mission_commander.rag.exception;

public class InvalidKnowledgeQueryException extends IllegalArgumentException {
    public InvalidKnowledgeQueryException(String message) { super(message); }
}

package pl.stalostech.ai_drone_mission_commander.agent;

public class IncompleteChatStreamException extends RuntimeException {
    public IncompleteChatStreamException() {
        super("The model stream ended without confirming completion.");
    }
}

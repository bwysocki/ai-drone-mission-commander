package pl.stalostech.ai_drone_mission_commander.agent.exception;

public class AgentToolException extends RuntimeException {
    public AgentToolException() {
        super("The agent could not complete its tool requests.");
    }
}

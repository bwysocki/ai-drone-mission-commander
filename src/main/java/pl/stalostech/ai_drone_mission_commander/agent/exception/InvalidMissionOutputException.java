package pl.stalostech.ai_drone_mission_commander.agent.exception;

public class InvalidMissionOutputException extends RuntimeException {
    public InvalidMissionOutputException() {
        super("The model did not return a valid mission intent.");
    }
}

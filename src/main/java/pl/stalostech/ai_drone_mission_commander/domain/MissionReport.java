package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.List;
import java.util.Objects;

/** Execution results must come from the simulator, not inferred model claims. */
public record MissionReport(MissionIntent intent, MissionOutcome outcome, String summary, List<String> observations) {
    public MissionReport {
        Objects.requireNonNull(intent, "intent must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        summary = DomainValues.text(summary, "summary");
        observations = DomainValues.texts(observations, "observations");
    }
}

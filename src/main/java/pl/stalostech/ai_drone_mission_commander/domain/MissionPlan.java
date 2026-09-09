package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.List;
import java.util.Objects;

/** A proposal; constructing a plan does not authorize execution. */
public record MissionPlan(MissionIntent intent, List<String> steps) {
    public MissionPlan {
        Objects.requireNonNull(intent, "intent must not be null");
        steps = DomainValues.texts(steps, "steps");
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("A plan must have at least one step");
        }
    }
}

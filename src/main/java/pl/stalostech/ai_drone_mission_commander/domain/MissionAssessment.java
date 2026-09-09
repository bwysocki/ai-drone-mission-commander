package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.List;
import java.util.Objects;

/** Reserved for results produced by deterministic Java safety rules. */
public record MissionAssessment(AssessmentStatus status, List<String> reasons) {
    public MissionAssessment {
        Objects.requireNonNull(status, "status must not be null");
        reasons = DomainValues.texts(reasons, "reasons");
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("An assessment must explain its status");
        }
    }
}

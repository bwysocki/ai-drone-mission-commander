package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.Objects;

public record Mission(String id, MissionIntent intent, MissionState state, Route route,
                      InspectionResult inspectionResult, String failureReason) {
    public Mission {
        id = DomainValues.text(id, "id");
        Objects.requireNonNull(intent);
        Objects.requireNonNull(state);
        Objects.requireNonNull(route);
        if (state == MissionState.FAILED) {
            failureReason = DomainValues.text(failureReason, "failureReason");
        } else if (failureReason != null) {
            throw new IllegalArgumentException("Only a failed mission has a failure reason");
        }
        if (inspectionResult != null && (state != MissionState.COMPLETED || intent.type() != MissionType.INSPECTION)) {
            throw new IllegalArgumentException("Inspection results require a completed inspection mission");
        }
    }
}

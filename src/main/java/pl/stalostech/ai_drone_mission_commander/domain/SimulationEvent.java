package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.Objects;

/** Sequence replaces wall-clock time so scenarios can be replayed deterministically. */
public record SimulationEvent(long sequence, SimulationEventType type, String droneId, int value) {
    public SimulationEvent {
        Objects.requireNonNull(type);
        if (sequence < 1 || value < 0) {
            throw new IllegalArgumentException("Invalid event");
        }
    }
}

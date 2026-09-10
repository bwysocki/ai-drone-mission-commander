package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.Objects;

public record Sector(String id, Position position, String description) {
    public Sector {
        id = DomainValues.text(id, "id");
        Objects.requireNonNull(position);
        description = DomainValues.text(description, "description");
    }
}

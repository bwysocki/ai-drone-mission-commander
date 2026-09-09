package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.Locale;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MissionIntent(
        @JsonProperty(required = true) String droneId,
        @JsonProperty(required = true) MissionType type,
        @JsonProperty(required = true) String targetSector,
        @JsonProperty(required = true) boolean returnHome) {

    public MissionIntent {
        droneId = DomainValues.text(droneId, "droneId").toLowerCase(Locale.ROOT);
        targetSector = DomainValues.text(targetSector, "targetSector").toUpperCase(Locale.ROOT);
        Objects.requireNonNull(type, "type must not be null");
        if (!droneId.matches("[a-z0-9][a-z0-9_-]*") || !targetSector.matches("[A-Z0-9][A-Z0-9_-]*")) {
            throw new IllegalArgumentException("Identifiers must contain only letters, digits, underscores or hyphens");
        }
    }
}

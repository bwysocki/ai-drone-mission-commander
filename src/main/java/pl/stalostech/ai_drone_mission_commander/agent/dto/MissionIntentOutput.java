package pl.stalostech.ai_drone_mission_commander.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import pl.stalostech.ai_drone_mission_commander.domain.MissionType;

/** Provider schema permits explicit unknown values; Java rejects them before creating a domain intent. */
public record MissionIntentOutput(
        @JsonProperty(required = true) @Schema(nullable = true) String droneId,
        @JsonProperty(required = true) @Schema(nullable = true) MissionType type,
        @JsonProperty(required = true) @Schema(nullable = true) String targetSector,
        @JsonProperty(required = true) boolean returnHome) {
}

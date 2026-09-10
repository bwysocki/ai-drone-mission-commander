package pl.stalostech.ai_drone_mission_commander.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.stalostech.ai_drone_mission_commander.domain.SimulationEventType;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SimulationEventRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "GPS_LOST") SimulationEventType type,
        @Schema(description = "Required for drone events; omit for STRONG_WIND", example = "alpha")
        @JsonDeserialize(using = ChatRequest.MessageDeserializer.class) String droneId,
        @Schema(description = "Battery percentage points (default 20) or wind km/h (default 45); omit for other events")
        @JsonDeserialize(using = ChatRequestOptions.TokenLimitDeserializer.class) Integer amount) {
}

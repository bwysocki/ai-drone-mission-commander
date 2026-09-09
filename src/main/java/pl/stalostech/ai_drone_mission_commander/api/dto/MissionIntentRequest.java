package pl.stalostech.ai_drone_mission_commander.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonDeserialize;

public record MissionIntentRequest(
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1,
                example = "Send Alpha to Bravo, inspect the area and return home.")
        @JsonDeserialize(using = ChatRequest.MessageDeserializer.class) String message) {
}

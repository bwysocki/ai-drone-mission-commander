package pl.stalostech.ai_drone_mission_commander.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ChatReply(
        @Schema(example = "Check battery, weather, GPS and the inspection area.") String message,
        Metadata metadata) {

    public record Metadata(
            @Schema(example = "chatcmpl-example") String id,
            @Schema(description = "Model reported by the provider") String model,
            @Schema(example = "STOP") String finishReason,
            @Schema(example = "20") Integer promptTokens,
            @Schema(example = "12") Integer completionTokens,
            @Schema(example = "32") Integer totalTokens) {
    }
}

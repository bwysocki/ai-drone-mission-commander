package pl.stalostech.ai_drone_mission_commander.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonDeserialize;

public record KnowledgeAskRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "What is the battery policy for a new inspection?")
        @JsonDeserialize(using = ChatRequest.MessageDeserializer.class) String message,
        @Schema(description = "Default true. Set false for the same prompt without retrieval.") Boolean useRag,
        RagOptions rag) {}

package pl.stalostech.ai_drone_mission_commander.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.stalostech.ai_drone_mission_commander.rag.KnowledgeTopic;
import pl.stalostech.ai_drone_mission_commander.rag.KnowledgeType;
import tools.jackson.databind.annotation.JsonDeserialize;

public record KnowledgeSearchRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 2000,
                example = "What should I check when the drone is running low on energy?")
        @JsonDeserialize(using = ChatRequest.MessageDeserializer.class) String query,
        @Schema(description = "Default 3", minimum = "1", maximum = "6")
        @JsonDeserialize(using = ChatRequestOptions.TokenLimitDeserializer.class) Integer topK,
        @Schema(description = "Default 0; cosine similarity cutoff, not a probability", minimum = "0", maximum = "1")
        Double similarityThreshold,
        KnowledgeType type, KnowledgeTopic topic) {}

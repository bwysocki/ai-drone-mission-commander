package pl.stalostech.ai_drone_mission_commander.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.stalostech.ai_drone_mission_commander.rag.KnowledgeTopic;
import pl.stalostech.ai_drone_mission_commander.rag.KnowledgeType;
import tools.jackson.databind.annotation.JsonDeserialize;

public record RagOptions(
        @Schema(description = "Default 3", minimum = "1", maximum = "6")
        @JsonDeserialize(using = ChatRequestOptions.TokenLimitDeserializer.class) Integer topK,
        @Schema(description = "Default 0; cosine similarity cutoff", minimum = "0", maximum = "1") Double similarityThreshold,
        KnowledgeType type, KnowledgeTopic topic,
        @Schema(description = "Optional standalone retrieval question for ambiguous follow-ups. Does not replace the user message.", maxLength = 2000)
        @JsonDeserialize(using = ChatRequest.MessageDeserializer.class) String query) {}

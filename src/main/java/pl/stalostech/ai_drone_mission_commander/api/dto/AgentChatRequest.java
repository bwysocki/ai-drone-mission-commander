package pl.stalostech.ai_drone_mission_commander.api.dto;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonDeserialize;

public record AgentChatRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1,
                example = "Summarize the selected mission and check the drone's current status.")
        @JsonDeserialize(using = ChatRequest.MessageDeserializer.class) String message,
        ChatRequestOptions options,
        @Schema(description = "Optional conversation UUID. Reuse it to load the last 20 user/assistant messages. Omit to start a new conversation.")
        UUID conversationId,
        @Schema(description = "Optional existing mission ID, returned by the Simulation API. Omit to select no mission.")
        @JsonDeserialize(using = ChatRequest.MessageDeserializer.class) String missionId,
        @Schema(description = "Omit for tools and memory only. Supply {} to enable retrieval, or select filters/query.")
        RagOptions rag) {}

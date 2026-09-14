package pl.stalostech.ai_drone_mission_commander.api.dto;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record AgentChatReply(String message, ChatReply.Metadata metadata,
        @Schema(description = "Conversation correlation ID; no messages are stored in milestone 7.") UUID conversationId) {}

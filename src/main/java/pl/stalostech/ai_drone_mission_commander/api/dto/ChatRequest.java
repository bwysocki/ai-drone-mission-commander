package pl.stalostech.ai_drone_mission_commander.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ChatRequest(
        @Schema(type = "string", description = "A nonblank question for the drone mission assistant",
                example = "What should a drone operator check before an inspection mission?",
                minLength = 1, requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonDeserialize(using = ChatRequest.MessageDeserializer.class) String message,
        ChatRequestOptions options) {

    public ChatRequest(String message) {
        this(message, null);
    }

    public static class MessageDeserializer extends ValueDeserializer<String> {

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (String) context.handleUnexpectedToken(String.class, parser);
            }
            return parser.getString();
        }
    }
}

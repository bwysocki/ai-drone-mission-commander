package pl.stalostech.ai_drone_mission_commander.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ChatRequestOptions(
        @Schema(description = "Optional model override; must be accessible to the server's API account")
        @JsonDeserialize(using = ChatRequest.MessageDeserializer.class) String model,
        @Schema(description = "Optional completion token limit, including reasoning tokens", minimum = "1")
        @JsonDeserialize(using = TokenLimitDeserializer.class) Integer maxCompletionTokens) {

    public static class TokenLimitDeserializer extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }
}

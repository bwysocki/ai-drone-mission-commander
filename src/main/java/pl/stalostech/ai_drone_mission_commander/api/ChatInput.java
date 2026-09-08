package pl.stalostech.ai_drone_mission_commander.api;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.stalostech.ai_drone_mission_commander.api.dto.ChatRequestOptions;

final class ChatInput {
    private ChatInput() {
    }

    static void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }
    }

    static OpenAiChatOptions.Builder options(ChatRequestOptions options) {
        if (options == null) {
            return null;
        }
        if (options.model() != null && options.model().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "model must not be blank");
        }
        if (options.maxCompletionTokens() != null && options.maxCompletionTokens() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxCompletionTokens must be positive");
        }
        var builder = OpenAiChatOptions.builder();
        if (options.model() != null) {
            builder.model(options.model());
        }
        if (options.maxCompletionTokens() != null) {
            builder.maxCompletionTokens(options.maxCompletionTokens());
        }
        return builder;
    }
}

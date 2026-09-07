package pl.stalostech.ai_drone_mission_commander.api;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import pl.stalostech.ai_drone_mission_commander.api.dto.ChatReply;

public final class ChatResponseMapper {

    private ChatResponseMapper() {
    }

    public static ChatReply toReply(ChatResponse response) {
        Generation generation = response == null ? null : response.getResult();
        AssistantMessage assistant = generation == null ? null : generation.getOutput();
        if (assistant == null || assistant.getText() == null || assistant.getText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The model returned no text answer");
        }

        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata.getUsage();
        return new ChatReply(assistant.getText(), new ChatReply.Metadata(
                metadata.getId(), metadata.getModel(), generation.getMetadata().getFinishReason(),
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens()));
    }
}

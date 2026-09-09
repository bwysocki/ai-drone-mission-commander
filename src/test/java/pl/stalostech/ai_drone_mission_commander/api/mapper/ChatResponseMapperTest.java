package pl.stalostech.ai_drone_mission_commander.api.mapper;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.stalostech.ai_drone_mission_commander.api.dto.ChatReply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatResponseMapperTest {

    @Test
    void mapsFirstCandidateAndBothLevelsOfMetadata() {
        var response = new ChatResponse(List.of(
                new Generation(new AssistantMessage("First answer"),
                        ChatGenerationMetadata.builder().finishReason("STOP").build()),
                new Generation(new AssistantMessage("Second answer"))),
                ChatResponseMetadata.builder().id("response-1").model("test-model")
                        .usage(new DefaultUsage(20, 12, 32)).build());
        assertThat(ChatResponseMapper.toReply(response)).isEqualTo(
                new ChatReply("First answer", new ChatReply.Metadata("response-1", "test-model", "STOP", 20, 12, 32)));
    }

    @ParameterizedTest
    @MethodSource("emptyResponses")
    void rejectsResponsesWithoutText(ChatResponse response) {
        assertThatThrownBy(() -> ChatResponseMapper.toReply(response))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    static Stream<ChatResponse> emptyResponses() {
        return Stream.of(null, new ChatResponse(List.of()),
                new ChatResponse(List.of(new Generation(new AssistantMessage("")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage(" \n\t")))));
    }
}

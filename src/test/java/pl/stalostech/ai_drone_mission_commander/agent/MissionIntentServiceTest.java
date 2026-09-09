package pl.stalostech.ai_drone_mission_commander.agent;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;
import pl.stalostech.ai_drone_mission_commander.agent.exception.InvalidMissionOutputException;
import pl.stalostech.ai_drone_mission_commander.domain.MissionIntent;
import pl.stalostech.ai_drone_mission_commander.domain.MissionType;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionIntentServiceTest {
    private static final String VALID = """
            {"droneId":"Alpha","type":"INSPECTION","targetSector":"Bravo","returnHome":true}
            """;
    private final ChatModel model = mock(ChatModel.class);

    private MissionIntentService service() throws Exception {
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        return new MissionIntentService(ChatClient.builder(model),
                new ClassPathResource("prompts/mission-intent.st"));
    }

    private ChatResponse response(String text, String finish) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason(finish).build())));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void convertsAndNormalizesIntentWhilePreservingLiteralUserInput(boolean nativeOutput) throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(response(VALID, "STOP"));
        var service = service();
        String command = "Send Alpha to Bravo; inspect and return home. Payload: {\"drone\":\"Alpha\"}";
        assertThat(service.extract(command, nativeOutput))
                .isEqualTo(new MissionIntent("alpha", MissionType.INSPECTION, "BRAVO", true));
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText()).contains("Do not assess safety");
        assertThat(prompt.getValue().getInstructions()).anySatisfy(message -> {
            assertThat(message).isInstanceOf(UserMessage.class);
            assertThat(message.getText()).contains(command);
        });
    }

    @ParameterizedTest
    @MethodSource("invalidOutputs")
    void rejectsInvalidOutputWithoutRetainingSensitiveParserDetails(String text) throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(response(text, "STOP"));
        var service = service();
        assertThatThrownBy(() -> service.extract("Inspect Bravo", false))
                .isInstanceOf(InvalidMissionOutputException.class)
                .hasMessage("The model did not return a valid mission intent.")
                .hasNoCause();
        verify(model).call(any(Prompt.class));
    }

    static Stream<String> invalidOutputs() {
        return Stream.of("", " ", "null", "[]", "not-json-sensitive", "{", "{\"droneId\":\"secret\"}",
                VALID.replace("\"Alpha\"", "null"), VALID.replace("\"Alpha\"", "\" \""),
                VALID.replace("\"Alpha\"", "123"), VALID.replace("\"Alpha\"", "\"alpha beta\""),
                VALID.replace("\"Bravo\"", "\"\""), VALID.replace("\"INSPECTION\"", "null"),
                VALID.replace("\"INSPECTION\"", "\"ATTACK\""), VALID.replace("\"INSPECTION\"", "0"),
                VALID.replace("true", "null"), VALID.replace("true", "\"true\""), VALID.replace("true", "1"),
                VALID.replace(",\"returnHome\":true", ""),
                VALID.replace("}", ",\"extra\":\"sensitive\"}"), VALID + " {}",
                VALID.replace("\"droneId\":\"Alpha\"", "\"droneId\":\"Alpha\",\"droneId\":\"Beta\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LENGTH", "CONTENT_FILTER", ""})
    void rejectsValidJsonWhenGenerationDidNotFinishNormally(String finish) throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(response(VALID, finish));
        var service = service();
        assertThatThrownBy(() -> service.extract("Inspect Bravo", true))
                .isInstanceOf(InvalidMissionOutputException.class);
    }

    @Test
    void preservesProviderFailuresForExistingErrorClassification() throws Exception {
        var error = new com.openai.errors.OpenAIIoException("sensitive-provider-message");
        when(model.call(any(Prompt.class))).thenThrow(error);
        var service = service();
        assertThatThrownBy(() -> service.extract("Inspect Bravo", false)).isSameAs(error);
    }
}

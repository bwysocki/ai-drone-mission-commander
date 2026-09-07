package pl.stalostech.ai_drone_mission_commander.agent;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesLiteralUserTextAndSystemInstructions(boolean directModel) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("Answer"))));
        when(model.call(any(Prompt.class))).thenReturn(response);
        ChatService service = new ChatService(ChatClient.builder(model), model);
        String question = "Explain this payload: {\"drone\":\"Alpha\"}";

        ChatResponse actual = directModel ? service.chatWithModel(question) : service.chat(question);

        assertThat(actual.getResult().getOutput().getText()).isEqualTo("Answer");
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions()).hasSize(2);
        assertThat(prompt.getValue().getInstructions().getFirst()).isInstanceOf(SystemMessage.class);
        assertThat(prompt.getValue().getInstructions().getFirst().getText()).contains("cannot execute drone missions");
        assertThat(prompt.getValue().getInstructions().getLast()).isInstanceOf(UserMessage.class);
        assertThat(prompt.getValue().getInstructions().getLast().getText()).isEqualTo(question);
    }
}

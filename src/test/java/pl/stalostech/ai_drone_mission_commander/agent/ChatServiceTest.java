package pl.stalostech.ai_drone_mission_commander.agent;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
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
    @ValueSource(strings = {"Answer with a short numbered checklist.",
            "Explain each preflight check and explicitly list missing information."})
    void bothCallStylesUseInstructionsFromTheResource(String instructions) throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Answer")))));
        var resource = new org.springframework.core.io.ByteArrayResource(
                instructions.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var service = new ChatService(ChatClient.builder(model), model, resource);

        service.chat("What should I check?");
        service.chatWithModel("What should I check?");

        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues()).allSatisfy(prompt ->
                assertThat(prompt.getSystemMessage().getText()).isEqualTo(instructions));
    }

    @Test
    void streamKeepsRealClientAndPropagatesCancellationToModel() throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        var cancelled = new AtomicBoolean();
        var chunk = new ChatResponse(List.of(new Generation(new AssistantMessage("First"))));
        when(model.stream(any(Prompt.class))).thenReturn(
                Flux.concat(Flux.just(chunk), Flux.<ChatResponse>never()).doOnCancel(() -> cancelled.set(true)));
        var service = new ChatService(ChatClient.builder(model), model,
                new org.springframework.core.io.ClassPathResource("prompts/mission-assistant.st"));
        var stream = service.stream("Question", null);
        verify(model, never()).stream(any(Prompt.class));
        StepVerifier.create(stream).expectNext("First").thenCancel().verify(Duration.ofSeconds(2));
        assertThat(cancelled).isTrue();
        verify(model).stream(any(Prompt.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesLiteralUserTextAndSystemInstructions(boolean directModel) throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("Answer"))));
        when(model.call(any(Prompt.class))).thenReturn(response);
        ChatService service = new ChatService(ChatClient.builder(model), model,
                new org.springframework.core.io.ClassPathResource("prompts/mission-assistant.st"));
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

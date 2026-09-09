package pl.stalostech.ai_drone_mission_commander.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import pl.stalostech.ai_drone_mission_commander.agent.exception.IncompleteChatStreamException;
import reactor.core.publisher.Flux;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final String systemPrompt;

    public ChatService(ChatClient.Builder builder, ChatModel chatModel,
                       @Value("classpath:prompts/mission-assistant.st") Resource promptResource) throws IOException {
        this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        this.chatClient = builder.defaultSystem(systemPrompt).build();
        this.chatModel = chatModel;
    }

    public ChatResponse chat(String message) {
        return chat(message, null);
    }

    public ChatResponse chat(String message, OpenAiChatOptions.Builder options) {
        return request(message, options).call().chatResponse();
    }

    public ChatResponse chatWithModel(String message) {
        return chatWithModel(message, null);
    }

    public ChatResponse chatWithModel(String message, OpenAiChatOptions.Builder options) {
        // Merge sparse overrides before build(): native options otherwise supply their own default model.
        var effectiveOptions = options == null ? null : OpenAiChatOptions.builder()
                .combineWith(chatModel.getOptions().mutate()).combineWith(options).build();
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(message)), effectiveOptions);
        return chatModel.call(prompt);
    }

    public Flux<String> stream(String message, OpenAiChatOptions.Builder options) {
        // Build a fresh request per subscription; cancellation travels to the model publisher.
        return Flux.defer(() -> {
            var finished = new AtomicBoolean();
            return request(message, options).stream().chatResponse()
                    .<String>handle((response, sink) -> {
                        var generation = response.getResult();
                        if (generation == null) {
                            return; // For example, a usage-only chunk.
                        }
                        var reason = generation.getMetadata().getFinishReason();
                        if (reason != null && !reason.isBlank()) {
                            finished.set(true);
                        }
                        var text = generation.getOutput().getText();
                        if (text != null && !text.isEmpty()) {
                            sink.next(text);
                        }
                    })
                    .concatWith(Flux.defer(() -> finished.get() ? Flux.empty()
                            : Flux.error(new IncompleteChatStreamException())));
        });
    }

    private ChatClient.ChatClientRequestSpec request(String message, OpenAiChatOptions.Builder options) {
        var request = chatClient.prompt().messages(new UserMessage(message));
        if (options != null) {
            request.options(options);
        }
        return request;
    }
}

package pl.stalostech.ai_drone_mission_commander.agent;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final String SYSTEM_MESSAGE = """
            You are an assistant for drone mission operators.
            Answer operational questions clearly. You cannot execute drone missions.
            """;

    private final ChatClient chatClient;
    private final ChatModel chatModel;

    public ChatService(ChatClient.Builder builder, ChatModel chatModel) {
        this.chatClient = builder.build();
        this.chatModel = chatModel;
    }

    public ChatResponse chat(String message) {
        return chatClient.prompt()
                .messages(new SystemMessage(SYSTEM_MESSAGE), new UserMessage(message))
                .call()
                .chatResponse();
    }

    public ChatResponse chatWithModel(String message) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_MESSAGE),
                new UserMessage(message)));
        return chatModel.call(prompt);
    }
}

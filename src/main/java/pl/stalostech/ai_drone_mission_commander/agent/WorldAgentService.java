package pl.stalostech.ai_drone_mission_commander.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import pl.stalostech.ai_drone_mission_commander.agent.exception.AgentToolException;
import pl.stalostech.ai_drone_mission_commander.tools.WorldToolRegistry;

@Service
@Profile("!simulator")
public class WorldAgentService {
    private static final Logger log = LoggerFactory.getLogger(WorldAgentService.class);
    private final ChatClient client;

    public WorldAgentService(ChatClient.Builder builder, WorldToolRegistry tools,
            @Value("classpath:prompts/world-agent.st") Resource prompt) throws IOException {
        client = builder.defaultSystem(prompt.getContentAsString(StandardCharsets.UTF_8))
                .defaultToolCallbacks(tools.callbacks()).build();
    }

    public ChatResponse chat(String message, OpenAiChatOptions.Builder options) {
        try {
            var request = client.prompt().messages(new UserMessage(message))
                    .advisors(new AgentIterationLogger());
            if (options != null) request.options(options);
            return request.call().chatResponse();
        } catch (IllegalStateException | ToolCallLimitExceededException exception) {
            // Framework failures such as an unknown tool name are not public diagnostic text.
            log.warn("Agent tool failure: code={}", exception instanceof ToolCallLimitExceededException
                    ? "TOOL_CALL_LIMIT_EXCEEDED" : "TOOL_ORCHESTRATION_FAILED");
            throw new AgentToolException();
        }
    }
}

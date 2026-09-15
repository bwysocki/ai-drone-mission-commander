package pl.stalostech.ai_drone_mission_commander.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
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
import pl.stalostech.ai_drone_mission_commander.agent.advisor.AgentRequestContext;
import pl.stalostech.ai_drone_mission_commander.agent.advisor.MissionContextAdvisor;
import pl.stalostech.ai_drone_mission_commander.agent.advisor.DevelopmentLoggingAdvisor;
import pl.stalostech.ai_drone_mission_commander.memory.ConversationMemory;

@Service
@Profile("!simulator")
public class WorldAgentService {
    private static final Logger log = LoggerFactory.getLogger(WorldAgentService.class);
    private final ChatClient client;
    private final ConversationMemory memory;

    public WorldAgentService(ChatClient.Builder builder, WorldToolRegistry tools,
            @Value("classpath:prompts/world-agent.st") Resource prompt,
            MissionContextAdvisor missionContext, List<DevelopmentLoggingAdvisor> developmentLoggers,
            ConversationMemory memory) throws IOException {
        this.memory = memory;
        builder.defaultAdvisors(missionContext);
        builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(memory)
                .order(ToolCallingAdvisor.DEFAULT_ORDER - 1).build());
        developmentLoggers.forEach(logger -> builder.defaultAdvisors(logger));
        client = builder.defaultSystem(prompt.getContentAsString(StandardCharsets.UTF_8))
                .defaultToolCallbacks(tools.callbacks()).build();
    }

    public ChatResponse chat(String message, OpenAiChatOptions.Builder options) {
        return chat(message, options, UUID.randomUUID(), null);
    }

    public ChatResponse chat(String message, OpenAiChatOptions.Builder options, UUID conversationId, String missionId) {
        return memory.inConversation(conversationId.toString(),
                () -> chatTurn(message, options, conversationId, missionId));
    }

    private ChatResponse chatTurn(String message, OpenAiChatOptions.Builder options, UUID conversationId, String missionId) {
        var context = new AgentRequestContext(UUID.randomUUID(), conversationId, missionId);
        try {
            var request = client.prompt().messages(new UserMessage(message))
                    .advisors(new AgentIterationLogger(context))
                    .advisors(spec -> spec.param(AgentRequestContext.KEY, context)
                            .param(ChatMemory.CONVERSATION_ID, conversationId.toString()));
            if (options != null) request.options(options);
            var response = request.call().chatResponse();
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                    || response.getResult().getOutput().getText() == null
                    || response.getResult().getOutput().getText().isBlank()) {
                throw new AgentToolException();
            }
            return response;
        } catch (IllegalStateException | ToolCallLimitExceededException exception) {
            // Framework failures such as an unknown tool name are not public diagnostic text.
            log.warn("Agent tool failure: code={}", exception instanceof ToolCallLimitExceededException
                    ? "TOOL_CALL_LIMIT_EXCEEDED" : "TOOL_ORCHESTRATION_FAILED");
            throw new AgentToolException();
        }
    }
}

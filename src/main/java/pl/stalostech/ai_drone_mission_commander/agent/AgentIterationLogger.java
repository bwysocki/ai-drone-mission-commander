package pl.stalostech.ai_drone_mission_commander.agent;

import pl.stalostech.ai_drone_mission_commander.agent.advisor.AgentRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/** One instance per request; runs inside the automatic tool loop. Logs no model-supplied text. */
final class AgentIterationLogger implements CallAdvisor {
    private static final Logger log = LoggerFactory.getLogger(AgentIterationLogger.class);
    private final AgentRequestContext context;
    private int iteration;

    AgentIterationLogger(AgentRequestContext context) { this.context = context; }

    @Override
    public String getName() { return "AgentIterationLogger"; }

    @Override
    public int getOrder() { return ToolCallingAdvisor.DEFAULT_ORDER + 1; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        int current = ++iteration;
        long priorToolResults = request.prompt().getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .mapToLong(message -> message.getResponses().size()).sum();
        log.info("Agent iteration: requestId={}, conversationId={}, iteration={}, phase=START, priorToolResults={}",
                context.requestId(), context.conversationId(), current, priorToolResults);
        try {
            var response = chain.nextCall(request);
            var chat = response.chatResponse();
            int toolCalls = chat == null || chat.getResult() == null ? 0
                    : chat.getResult().getOutput().getToolCalls().size();
            log.info("Agent iteration: requestId={}, conversationId={}, iteration={}, phase=RESPONSE, toolCalls={}",
                    context.requestId(), context.conversationId(), current, toolCalls);
            return response;
        } catch (RuntimeException exception) {
            log.warn("Agent iteration: requestId={}, conversationId={}, iteration={}, phase=FAILED",
                    context.requestId(), context.conversationId(), current);
            throw exception;
        }
    }
}

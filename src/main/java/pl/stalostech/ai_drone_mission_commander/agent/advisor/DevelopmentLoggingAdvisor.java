package pl.stalostech.ai_drone_mission_commander.agent.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev & !simulator")
public class DevelopmentLoggingAdvisor implements CallAdvisor {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentLoggingAdvisor.class);

    @Override
    public String getName() { return "DevelopmentLoggingAdvisor"; }

    @Override
    public int getOrder() { return ToolCallingAdvisor.DEFAULT_ORDER - 2; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        var context = (AgentRequestContext) request.context().get(AgentRequestContext.KEY);
        if (log.isDebugEnabled()) {
            var advisors = chain.getCallAdvisors().stream()
                    .map(advisor -> advisor.getName() + "(" + advisor.getOrder() + ")").toList();
            log.debug("Agent pipeline: requestId={}, conversationId={}, phase=START, advisors={}",
                    context.requestId(), context.conversationId(), advisors);
        }
        try {
            var response = chain.nextCall(request);
            log.debug("Agent pipeline: requestId={}, conversationId={}, phase=COMPLETE",
                    context.requestId(), context.conversationId());
            return response;
        } catch (RuntimeException exception) {
            log.debug("Agent pipeline: requestId={}, conversationId={}, phase=FAILED",
                    context.requestId(), context.conversationId());
            throw exception;
        }
    }
}

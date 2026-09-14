package pl.stalostech.ai_drone_mission_commander.agent.advisor;

import java.util.Objects;
import java.util.UUID;

/** Request-local metadata, not conversation memory or a model message. */
public record AgentRequestContext(UUID requestId, UUID conversationId, String missionId) {
    public static final String KEY = AgentRequestContext.class.getName();

    public AgentRequestContext {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(conversationId);
    }
}

package pl.stalostech.ai_drone_mission_commander.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!simulator")
public class KnowledgeAnswerService {
    private final ChatClient client;

    public KnowledgeAnswerService(ChatClient.Builder builder) {
        client = builder.defaultSystem("You explain fictional drone procedures. You have no live telemetry or tools. "
                + KnowledgeRag.RULES).build();
    }

    public ChatResponse answer(String message, KnowledgeRag.Session rag) {
        var request = client.prompt().messages(new UserMessage(message));
        if (rag != null) request.advisors(rag.advisor());
        return request.call().chatResponse();
    }
}

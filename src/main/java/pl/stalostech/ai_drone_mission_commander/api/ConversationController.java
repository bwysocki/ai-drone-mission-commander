package pl.stalostech.ai_drone_mission_commander.api;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.stalostech.ai_drone_mission_commander.api.dto.ConversationMessage;
import pl.stalostech.ai_drone_mission_commander.memory.ConversationMemory;

@RestController
@Profile("!simulator")
@RequestMapping("/api/agent/conversations")
@Tag(name = "Conversation memory", description = "In-memory message windows, separate from simulator state")
public class ConversationController {
    private final ConversationMemory memory;

    public ConversationController(ConversationMemory memory) { this.memory = memory; }

    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "Inspect stored messages", description = "At most 20 user/assistant messages. Unknown or cleared conversations return an empty list. No provider call.")
    public List<ConversationMessage> messages(@PathVariable UUID conversationId) {
        return memory.get(conversationId.toString()).stream()
                .map(message -> new ConversationMessage(message.getMessageType().getValue(), message.getText())).toList();
    }

    @DeleteMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Forget a conversation", description = "Clears messages without resetting drones or missions. Idempotent; no provider call.")
    public void clear(@PathVariable UUID conversationId) { memory.clear(conversationId.toString()); }
}

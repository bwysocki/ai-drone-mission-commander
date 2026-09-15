package pl.stalostech.ai_drone_mission_commander.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import pl.stalostech.ai_drone_mission_commander.agent.WorldAgentService;
import pl.stalostech.ai_drone_mission_commander.api.dto.AgentChatReply;
import pl.stalostech.ai_drone_mission_commander.api.dto.AgentChatRequest;
import pl.stalostech.ai_drone_mission_commander.api.mapper.ChatRequestMapper;
import pl.stalostech.ai_drone_mission_commander.api.mapper.ChatResponseMapper;
import pl.stalostech.ai_drone_mission_commander.tools.WorldToolRegistry;

@RestController
@Profile("!simulator")
@RequestMapping(value = "/api/agent", produces = "application/json")
@Tag(name = "World agent", description = "Answers using seven read-only simulator tools")
public class WorldAgentController {
    private final WorldAgentService agent;
    private final WorldToolRegistry tools;

    public WorldAgentController(WorldAgentService agent, WorldToolRegistry tools) {
        this.agent = agent; this.tools = tools;
    }

    @PostMapping("/chat")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answer based on available tool results"),
            @ApiResponse(responseCode = "400", description = "Invalid message or options", content = @Content),
            @ApiResponse(responseCode = "404", description = "Selected mission does not exist", content = @Content),
            @ApiResponse(responseCode = "502", description = "Tool orchestration failed or provider returned an invalid answer", content = @Content),
            @ApiResponse(responseCode = "503", description = "Provider unavailable or rate limited", content = @Content)
    })
    @Operation(summary = "Ask about the live simulated world",
            description = "The model may call read-only tools before answering. Reuse conversationId to load the last 20 user/assistant messages; omit it for a new conversation. Includes fresh simulator context and an optional missionId selected per request. History is not authoritative telemetry. Does not execute or approve missions. Missing selected missions return 404; unrecoverable agent errors return 502 and provider errors 502/503.")
    public AgentChatReply chat(@RequestBody AgentChatRequest request) {
        ChatRequestMapper.validateMessage(request.message());
        if (request.missionId() != null && request.missionId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missionId must not be blank");
        }
        UUID conversationId = request.conversationId() == null ? UUID.randomUUID() : request.conversationId();
        String missionId = request.missionId() == null ? null : request.missionId().strip();
        var reply = ChatResponseMapper.toReply(agent.chat(request.message(),
                ChatRequestMapper.options(request.options()), conversationId, missionId));
        return new AgentChatReply(reply.message(), reply.metadata(), conversationId);
    }

    @GetMapping("/tools")
    @Operation(summary = "Inspect generated tool definitions", description = "Returns names, descriptions and JSON input schemas without calling a model. inputSchema is a JSON-encoded string.")
    public List<ToolDefinition> definitions() { return tools.definitions(); }
}

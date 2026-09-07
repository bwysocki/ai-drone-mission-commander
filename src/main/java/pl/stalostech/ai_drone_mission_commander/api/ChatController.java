package pl.stalostech.ai_drone_mission_commander.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import pl.stalostech.ai_drone_mission_commander.agent.ChatService;
import pl.stalostech.ai_drone_mission_commander.api.dto.ChatReply;
import pl.stalostech.ai_drone_mission_commander.api.dto.ChatRequest;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "Two ways to ask the same model a question")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Answer text and model response metadata",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatReply.class))),
        @ApiResponse(responseCode = "400", description = "message must be a nonblank JSON string",
                content = @Content),
        @ApiResponse(responseCode = "502", description = "Provider rejected the request or returned no text",
                content = @Content),
        @ApiResponse(responseCode = "503", description = "Provider unavailable, rate limited or connection failed",
                content = @Content)
})
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @Operation(summary = "Ask using ChatClient",
            description = "Send a question through the fluent ChatClient API. Returns the answer and token usage.")
    public ChatReply chat(@RequestBody ChatRequest request) {
        validate(request);
        return ChatResponseMapper.toReply(chatService.chat(request.message()));
    }

    @PostMapping("/model")
    @Operation(summary = "Ask using ChatModel",
            description = "Call ChatModel directly with a Prompt. Uses the same instructions as /api/chat.")
    public ChatReply chatWithModel(@RequestBody ChatRequest request) {
        validate(request);
        return ChatResponseMapper.toReply(chatService.chatWithModel(request.message()));
    }

    private static void validate(ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }
    }
}

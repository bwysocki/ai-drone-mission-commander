package pl.stalostech.ai_drone_mission_commander.api;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.openai.errors.OpenAIException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;
import org.springframework.web.server.ResponseStatusException;
import pl.stalostech.ai_drone_mission_commander.agent.ChatService;
import pl.stalostech.ai_drone_mission_commander.agent.exception.IncompleteChatStreamException;
import pl.stalostech.ai_drone_mission_commander.api.dto.ChatRequestOptions;
import pl.stalostech.ai_drone_mission_commander.api.dto.ChatStreamEvent;
import pl.stalostech.ai_drone_mission_commander.api.mapper.ChatRequestMapper;
import reactor.core.publisher.Flux;

@RestController
@Profile("!simulator")
@Tag(name = "Chat")
public class ChatStreamController {
    private static final Logger log = LoggerFactory.getLogger(ChatStreamController.class);
    private final ChatService service;
    private final ChatExceptionHandler errors;

    public ChatStreamController(ChatService service, ChatExceptionHandler errors) {
        this.service = service;
        this.errors = errors;
    }

    @GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream an answer using ChatClient",
            description = "SSE events: delta contains JSON text, done marks success, error contains a sanitized problem. "
                    + "Provider errors are SSE events with HTTP 200; invalid input is HTTP 400. "
                    + "Close the connection after done or error. Use curl -N to observe incremental output.")
    @ApiResponse(responseCode = "200", description = "SSE delta, done or error events",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(implementation = ChatStreamEvent.class)))
    @ApiResponse(responseCode = "400", description = "Invalid message or options", content = @Content)
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(
            @RequestParam String message,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Integer maxCompletionTokens) {
        ChatRequestMapper.validateMessage(message);
        var options = ChatRequestMapper.options(new ChatRequestOptions(model, maxCompletionTokens));
        return Flux.defer(() -> {
            var hasText = new AtomicBoolean();
            return service.stream(message, options)
                .filter(text -> !text.isEmpty())
                .doOnNext(text -> {
                    if (!text.isBlank()) {
                        hasText.set(true);
                    }
                })
                .map(text -> event("delta", new ChatStreamEvent(text, null)))
                .concatWith(Flux.defer(() -> hasText.get()
                        ? Flux.just(event("done", new ChatStreamEvent(null, null)))
                        : Flux.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The model returned no text answer"))));
        }).onErrorResume(error -> Flux.just(event("error", new ChatStreamEvent(null, problem(error)))));
    }

    private ProblemDetail problem(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) {
            error = error.getCause();
        }
        if (error instanceof OpenAIException providerError) {
            return errors.handleProviderError(providerError);
        }
        if (error instanceof IncompleteChatStreamException) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                    "The model stream ended before the answer was complete.");
        }
        if (error instanceof ResponseStatusException responseError) {
            return ProblemDetail.forStatusAndDetail(responseError.getStatusCode(), "The model returned no text answer");
        }
        log.warn("Chat stream failure: exceptionType={}", error.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "The answer stream could not be completed.");
    }

    private static ServerSentEvent<ChatStreamEvent> event(String name, ChatStreamEvent data) {
        return ServerSentEvent.<ChatStreamEvent>builder(data).event(name).build();
    }
}

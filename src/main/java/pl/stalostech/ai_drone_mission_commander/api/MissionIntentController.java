package pl.stalostech.ai_drone_mission_commander.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.stalostech.ai_drone_mission_commander.agent.MissionIntentService;
import pl.stalostech.ai_drone_mission_commander.api.dto.MissionIntentRequest;
import pl.stalostech.ai_drone_mission_commander.api.mapper.ChatRequestMapper;
import pl.stalostech.ai_drone_mission_commander.domain.MissionIntent;

@RestController
@Tag(name = "Missions", description = "Typed intent extraction; no safety assessment or execution")
public class MissionIntentController {
    private final MissionIntentService service;

    public MissionIntentController(MissionIntentService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/missions/intent", produces = "application/json")
    @Operation(summary = "Extract a mission intent",
            description = "Convert one command into a validated MissionIntent. Set nativeOutput=true to send "
                    + "the JSON schema to a supporting provider; otherwise the schema is added to the prompt. "
                    + "Missing or ambiguous mission details may produce invalid output (502). No mission is executed.")
    @ApiResponse(responseCode = "200", description = "Validated mission intent",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MissionIntent.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content)
    @ApiResponse(responseCode = "502", description = "Invalid model output or rejected provider request", content = @Content)
    @ApiResponse(responseCode = "503", description = "AI provider unavailable", content = @Content)
    public MissionIntent extract(@RequestBody MissionIntentRequest request,
            @RequestParam(defaultValue = "false") boolean nativeOutput) {
        ChatRequestMapper.validateMessage(request.message());
        return service.extract(request.message(), nativeOutput);
    }
}

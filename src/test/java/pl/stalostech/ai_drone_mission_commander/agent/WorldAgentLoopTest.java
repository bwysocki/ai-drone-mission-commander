package pl.stalostech.ai_drone_mission_commander.agent;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import pl.stalostech.ai_drone_mission_commander.agent.exception.AgentToolException;
import pl.stalostech.ai_drone_mission_commander.simulation.*;
import pl.stalostech.ai_drone_mission_commander.tools.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class WorldAgentLoopTest {
    private final DroneWorld world = new DroneWorld();
    private final RouteService routes = new RouteService(world);
    private final DroneSimulationService drones = new DroneSimulationService(world, routes);
    private final SectorService sectors = new SectorService(world);
    private final WorldToolRegistry tools = new WorldToolRegistry(new DroneTools(drones),
            new WeatherTools(new WeatherService(world)),
            new MissionTools(sectors, routes, new MissionSimulationService(world, routes, drones, sectors),
                    new SimulationEventService(world)));
    private final ChatModel model = mock(ChatModel.class);
    private final JsonMapper json = new JsonMapper();

    private WorldAgentService agent() throws Exception {
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        return new WorldAgentService(ChatClient.builder(model), tools, new ClassPathResource("prompts/world-agent.st"),
                new pl.stalostech.ai_drone_mission_commander.agent.advisor.MissionContextAdvisor(world), List.of(), new pl.stalostech.ai_drone_mission_commander.memory.ConversationMemory());
    }

    private static AssistantMessage.ToolCall call(String id, String name, String arguments) {
        return new AssistantMessage.ToolCall(id, "function", name, arguments);
    }

    private static ChatResponse calls(AssistantMessage.ToolCall... calls) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .content("").toolCalls(List.of(calls)).build())));
    }

    private static ChatResponse answer() {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("private-answer-marker"))));
    }

    private static List<ToolResponseMessage.ToolResponse> results(Prompt prompt) {
        return prompt.getInstructions().stream().filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast).flatMap(message -> message.getResponses().stream()).toList();
    }

    @Test
    void automaticLoopHandlesSequentialAndBatchedToolRequests(CapturedOutput output) throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(
                calls(call("drone-1", "getDroneStatus", "{\"droneId\":\"alpha\"}")),
                calls(call("weather-1", "getWeather", "{}"),
                        call("route-1", "calculateRoute", "{\"droneId\":\"alpha\",\"sectorId\":\"SECTOR_B\",\"returnHome\":true}")),
                answer());
        var before = world.snapshot();
        assertThat(agent().chat("private-question-marker", null).getResult().getOutput().getText())
                .isEqualTo("private-answer-marker");
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(prompts.capture());
        var second = prompts.getAllValues().get(1);
        assertThat(results(second)).extracting(ToolResponseMessage.ToolResponse::id).containsExactly("drone-1");
        assertThat(json.readTree(results(second).getFirst().responseData()).path("batteryPercent").asInt()).isEqualTo(82);
        var third = prompts.getAllValues().get(2);
        assertThat(results(third)).extracting(ToolResponseMessage.ToolResponse::id)
                .containsExactly("drone-1", "weather-1", "route-1");
        assertThat(json.readTree(results(third).get(2).responseData()).path("estimatedBatteryUsage").asInt()).isEqualTo(20);
        assertThat(third.getInstructions().stream().filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast).flatMap(message -> message.getToolCalls().stream()))
                .extracting(AssistantMessage.ToolCall::arguments)
                .contains("{\"droneId\":\"alpha\"}", "{\"droneId\":\"alpha\",\"sectorId\":\"SECTOR_B\",\"returnHome\":true}");
        assertThat(world.snapshot()).isEqualTo(before);
        assertThat(output.getAll()).contains("iteration=1, phase=START, priorToolResults=0",
                "iteration=1, phase=RESPONSE, toolCalls=1",
                "iteration=2, phase=START, priorToolResults=1",
                "iteration=2, phase=RESPONSE, toolCalls=2",
                "iteration=3, phase=START, priorToolResults=3",
                "iteration=3, phase=RESPONSE, toolCalls=0")
                .doesNotContain("private-question-marker", "private-answer-marker", "drone-1", "SECTOR_B");
    }

    @Test
    void errorResultCanBeFollowedByCorrectedToolArguments() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(
                calls(call("missing", "getDroneStatus", "{\"droneId\":\"missing-drone\"}")),
                calls(call("corrected", "getDroneStatus", "{\"droneId\":\"alpha\"}")), answer());
        agent().chat("Check Alpha", null);
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(prompts.capture());
        assertThat(results(prompts.getAllValues().get(1)).getFirst().responseData()).contains("NOT_FOUND");
        assertThat(json.readTree(results(prompts.getAllValues().get(2)).get(1).responseData())
                .path("batteryPercent").asInt()).isEqualTo(82);
    }

    @Test
    void manualSingleCycleMatchesAutomaticConversationHistory() throws Exception {
        var requested = calls(call("drone-1", "getDroneStatus", "{\"droneId\":\"alpha\"}"));
        when(model.call(any(Prompt.class))).thenReturn(requested, answer());
        var service = agent();
        service.chat("Read Alpha", null);
        var captured = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(captured.capture());
        Prompt initial = captured.getAllValues().getFirst();
        Prompt automaticFollowUp = captured.getAllValues().getLast();

        // Educational manual cycle only. The production endpoint retains the automatic advisor.
        reset(model);
        when(model.call(any(Prompt.class))).thenReturn(requested, answer());
        var manager = ToolCallingManager.builder().build();
        ChatResponse response = model.call(initial);
        var execution = manager.executeToolCalls(initial, response);
        var followUp = new Prompt(execution.conversationHistory(), initial.getOptions());
        ChatResponse finalResponse = model.call(followUp);

        assertThat(followUp.getInstructions()).isEqualTo(automaticFollowUp.getInstructions());
        assertThat(finalResponse.getResult().getOutput().getText()).isEqualTo("private-answer-marker");
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void eachRequestStartsANewTraceAndCounter(CapturedOutput output) throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(answer());
        var service = agent();
        service.chat("first private request", null);
        service.chat("second private request", null);
        var starts = output.getAll().lines().filter(line -> line.contains("phase=START")).toList();
        assertThat(starts).hasSize(2).allSatisfy(line -> assertThat(line).contains("iteration=1"));
        var ids = starts.stream().map(line -> line.split("requestId=")[1].split(",")[0]).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void failedIterationDoesNotLogExceptionDetails(CapturedOutput output) throws Exception {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("private-failure-marker"));
        assertThatThrownBy(() -> agent().chat("private-question-marker", null)).isInstanceOf(AgentToolException.class);
        assertThat(output.getAll()).contains("iteration=1, phase=FAILED")
                .doesNotContain("private-failure-marker", "private-question-marker");
    }
}

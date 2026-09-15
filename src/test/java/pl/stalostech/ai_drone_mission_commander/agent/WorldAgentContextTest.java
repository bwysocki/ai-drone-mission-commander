package pl.stalostech.ai_drone_mission_commander.agent;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import pl.stalostech.ai_drone_mission_commander.agent.advisor.*;
import pl.stalostech.ai_drone_mission_commander.domain.*;
import pl.stalostech.ai_drone_mission_commander.simulation.*;
import pl.stalostech.ai_drone_mission_commander.simulation.exception.SimulationNotFoundException;
import pl.stalostech.ai_drone_mission_commander.tools.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class WorldAgentContextTest {
    private final DroneWorld world = new DroneWorld();
    private final RouteService routes = new RouteService(world);
    private final DroneSimulationService drones = new DroneSimulationService(world, routes);
    private final SectorService sectors = new SectorService(world);
    private final MissionSimulationService missions = new MissionSimulationService(world, routes, drones, sectors);
    private final WorldToolRegistry tools = new WorldToolRegistry(new DroneTools(drones),
            new WeatherTools(new WeatherService(world)),
            new MissionTools(sectors, routes, missions, new SimulationEventService(world)));
    private final ChatModel model = mock(ChatModel.class);

    private WorldAgentService service(boolean dev) throws Exception {
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").maxCompletionTokens(2048).build());
        return new WorldAgentService(ChatClient.builder(model), tools, new ClassPathResource("prompts/world-agent.st"),
                new MissionContextAdvisor(world), dev ? List.of(new DevelopmentLoggingAdvisor()) : List.of(), new pl.stalostech.ai_drone_mission_commander.memory.ConversationMemory());
    }

    private static ChatResponse answer() {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("private-answer"))));
    }

    private static ChatResponse toolCall() {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getWeather", "{}"))).build())));
    }

    private static String context(Prompt prompt) {
        var messages = prompt.getInstructions().stream().filter(SystemMessage.class::isInstance)
                .map(message -> message.getText()).filter(text -> text.startsWith("APPLICATION CONTEXT")).toList();
        assertThat(messages).hasSize(1);
        return messages.getFirst();
    }

    @Test
    void contextSurvivesToolLoopOnceAndPreservesOptionsAndLiteralInput() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(toolCall(), answer());
        var mission = missions.create(new MissionIntent("alpha", MissionType.INSPECTION, "SECTOR_B", true));
        var before = world.snapshot();
        var conversationId = UUID.randomUUID();
        String user = "Check {\"mission\":\"alpha\"}, literal {placeholder}";
        service(false).chat(user, OpenAiChatOptions.builder().maxCompletionTokens(128), conversationId, mission.id());
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        for (Prompt prompt : prompts.getAllValues()) {
            assertThat(context(prompt)).contains("in-memory drone simulator", "alpha", "SECTOR_B",
                    mission.id(), "CREATED").doesNotContain(conversationId.toString());
            assertThat(prompt.getSystemMessage().getText()).contains("read-only tools");
            assertThat(prompt.getUserMessage().getText()).isEqualTo(user);
            var options = (OpenAiChatOptions) prompt.getOptions();
            assertThat(options.getModel()).isEqualTo("test-model");
            assertThat(options.getMaxCompletionTokens()).isEqualTo(128);
            assertThat(options.getToolCallbacks()).hasSize(7);
        }
        assertThat(prompts.getAllValues().getLast().getInstructions())
                .anyMatch(ToolResponseMessage.class::isInstance);
        assertThat(world.snapshot()).isEqualTo(before);
    }

    @Test
    void refreshesMissionAndDoesNotRememberSelectionsEvenWithTheSameConversationId() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(answer());
        var service = service(false);
        var mission = missions.create(new MissionIntent("alpha", MissionType.INSPECTION, "SECTOR_B", true));
        var conversation = UUID.randomUUID();
        service.chat("first-private-question", null, conversation, mission.id());
        missions.execute(mission.id());
        service.chat("second", null, conversation, mission.id());
        service.chat("third", null, conversation, null);
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(prompts.capture());
        assertThat(context(prompts.getAllValues().get(0))).contains("CREATED");
        assertThat(context(prompts.getAllValues().get(1))).contains("COMPLETED");
        assertThat(context(prompts.getAllValues().get(2))).contains("\"currentMission\":null").doesNotContain(mission.id());
        assertThat(prompts.getAllValues().get(2).getInstructions()).hasSize(7)
                .anyMatch(message -> message.getText().contains("first-private-question"));
        world.reset();
        assertThatThrownBy(() -> service.chat("after reset", null, conversation, mission.id()))
                .isInstanceOf(SimulationNotFoundException.class);
        verify(model, times(3)).call(any(Prompt.class));
    }

    @Test
    void concurrentRequestsKeepTheirMissionAndConversationSeparate() throws Exception {
        var service = service(false);
        var first = missions.create(new MissionIntent("alpha", MissionType.INSPECTION, "SECTOR_B", true));
        var second = missions.create(new MissionIntent("charlie", MissionType.PATROL, "SECTOR_C", false));
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String expected = prompt.getUserMessage().getText();
            String other = expected.equals(first.id()) ? second.id() : first.id();
            assertThat(context(prompt)).contains("\"id\":\"" + expected + "\"").doesNotContain("\"id\":\"" + other + "\"");
            return answer();
        });
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = List.<java.util.concurrent.Callable<ChatResponse>>of(
                    () -> service.chat(first.id(), null, UUID.randomUUID(), first.id()),
                    () -> service.chat(second.id(), null, UUID.randomUUID(), second.id()));
            for (var future : executor.invokeAll(tasks)) assertThat(future.get()).isNotNull();
        }
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void developmentLoggerShowsAdvisorOrderOnceWithoutRawData(CapturedOutput output) throws Exception {
        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(DevelopmentLoggingAdvisor.class);
        var previous = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            when(model.call(any(Prompt.class))).thenReturn(toolCall(), answer());
            service(true).chat("private-user-payload", null);
            assertThat(output.getAll()).contains("MissionContextAdvisor(", "Tool Calling Advisor(", "AgentIterationLogger(")
                    .doesNotContain("private-user-payload", "private-answer", "APPLICATION CONTEXT", "SECTOR_A");
            assertThat(output.getAll().lines().filter(line -> line.contains("Agent pipeline:") && line.contains("phase=START")))
                    .hasSize(1);
            assertThat(output.getAll().lines().filter(line -> line.contains("Agent iteration:") && line.contains("phase=START")))
                    .hasSize(2);
            var chain = output.getAll().lines().filter(line -> line.contains("advisors=")).findFirst().orElseThrow();
            assertThat(chain.indexOf("MissionContextAdvisor(")).isLessThan(chain.indexOf("Tool Calling Advisor("));
            assertThat(chain.indexOf("Tool Calling Advisor(")).isLessThan(chain.indexOf("AgentIterationLogger("));
        } finally {
            logger.setLevel(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "dev", "dev,simulator"})
    void developmentAdvisorIsEnabledOnlyByTheDevProfile(String profiles) {
        new ApplicationContextRunner().withUserConfiguration(DevelopmentLoggingAdvisor.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles.split(",")))
                .run(context -> assertThat(context.getBeansOfType(DevelopmentLoggingAdvisor.class))
                        .hasSize(profiles.equals("dev") ? 1 : 0));
    }
}

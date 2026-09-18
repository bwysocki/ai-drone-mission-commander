package pl.stalostech.ai_drone_mission_commander.rag;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;
import pl.stalostech.ai_drone_mission_commander.agent.WorldAgentService;
import pl.stalostech.ai_drone_mission_commander.agent.advisor.MissionContextAdvisor;
import pl.stalostech.ai_drone_mission_commander.domain.SimulationEventType;
import pl.stalostech.ai_drone_mission_commander.memory.ConversationMemory;
import pl.stalostech.ai_drone_mission_commander.simulation.*;
import pl.stalostech.ai_drone_mission_commander.tools.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeRagTest {
    private final ChatModel model = mock(ChatModel.class);
    private final EmbeddingModel embedding = mock(EmbeddingModel.class);
    private final KnowledgeSearchService search = new KnowledgeSearchService(new KnowledgeCatalog(), embedding, new KnowledgePipeline());
    private final KnowledgeRag rag = new KnowledgeRag(search);

    @BeforeEach
    void models() {
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(embedding.embed(any(Document.class))).thenReturn(new float[]{1, 0});
        when(embedding.embed(anyString())).thenReturn(new float[]{1, 0});
        when(model.call(any(Prompt.class))).thenReturn(answer("Scripted answer"));
    }

    private static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void policyAnswerUsesActualContextAndOverrideOnlyChangesRetrieval() {
        var service = new KnowledgeAnswerService(ChatClient.builder(model));
        var session = rag.prepare(new RagSelection("  battery\n policy  ", 6, 0, KnowledgeType.SAFETY, KnowledgeTopic.BATTERY));
        service.answer("What about a new inspection?", session);
        var captured = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captured.capture());
        assertThat(captured.getValue().getUserMessage().getText()).startsWith("What about a new inspection?")
                .contains("below 20%", "battery-policy.md", "RETRIEVED PROCEDURES");
        assertThat(session.query()).isEqualTo("battery policy");
        assertThat(session.documents()).isNotEmpty().allSatisfy(d -> assertThat(d.getMetadata()).containsEntry("topic", "BATTERY"));
        verify(embedding).embed("battery policy");
        clearInvocations(model, embedding);
        service.answer("What about a new inspection?", null);
        verifyNoInteractions(embedding);
        verify(model).call(captured.capture());
        assertThat(captured.getValue().getUserMessage().getText()).isEqualTo("What about a new inspection?");
    }

    @Test
    void emptyFilteredRetrievalDoesNotFabricateContextOrReusePreviousSources() {
        var answers = new KnowledgeAnswerService(ChatClient.builder(model));
        var first = rag.prepare(new RagSelection("battery", 6, 0, KnowledgeType.SAFETY, KnowledgeTopic.BATTERY));
        answers.answer("battery policy", first);
        var empty = rag.prepare(new RagSelection("battery", 6, 0, KnowledgeType.PROCEDURE, KnowledgeTopic.BATTERY));
        answers.answer("battery policy", empty);
        assertThat(empty.documents()).isEmpty();
        assertThat(first.documents()).isNotEmpty();
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getValue().getUserMessage().getText()).contains("reference data):\n[]").doesNotContain("below 20%");
        assertThat(prompts.getValue().getSystemMessage().getText()).contains("do not invent a policy");
    }

    @Test
    void agentCombinesLiveEighteenPercentWithPolicyWithoutPollutingMemory() throws Exception {
        var world = new DroneWorld();
        var routes = new RouteService(world);
        var drones = new DroneSimulationService(world, routes);
        var sectors = new SectorService(world);
        var events = new SimulationEventService(world);
        events.inject(SimulationEventType.BATTERY_DROP, "alpha", 64);
        var before = world.snapshot();
        var tools = new WorldToolRegistry(new DroneTools(drones), new WeatherTools(new WeatherService(world)),
                new MissionTools(sectors, routes, new MissionSimulationService(world, routes, drones, sectors), events));
        var memory = new ConversationMemory();
        var agent = new WorldAgentService(ChatClient.builder(model), tools, new ClassPathResource("prompts/world-agent.st"),
                new MissionContextAdvisor(world), List.of(), memory);
        var calls = List.of(new AssistantMessage.ToolCall("battery", "function", "getDroneStatus", "{\"droneId\":\"alpha\"}"),
                new AssistantMessage.ToolCall("weather", "function", "getWeather", "{}"));
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().content("").toolCalls(calls).build()))), answer("Do not start a new inspection: Alpha has 18% and policy requires at least 20%."));
        var session = rag.prepare(new RagSelection("inspection battery policy", 6, 0, KnowledgeType.SAFETY, KnowledgeTopic.BATTERY));
        var id = UUID.randomUUID();
        agent.chat("Can Alpha start a new inspection?", null, id, null, session);
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getValue().getUserMessage().getText()).contains("below 20%", "battery-policy.md");
        var toolResults = prompts.getValue().getInstructions().stream().filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast).flatMap(m -> m.getResponses().stream()).toList();
        assertThat(toolResults).hasSize(2).anySatisfy(r -> assertThat(r.responseData()).contains("\"batteryPercent\":18"));
        assertThat(toolResults).anySatisfy(r -> assertThat(r.responseData()).contains("\"rain\":false", "GOOD"));
        verify(embedding, times(1)).embed("inspection battery policy");
        assertThat(memory.get(id.toString())).hasSize(2);
        assertThat(memory.get(id.toString()).getFirst().getText()).isEqualTo("Can Alpha start a new inspection?");
        assertThat(world.snapshot()).isEqualTo(before);

        clearInvocations(embedding, model);
        when(model.call(any(Prompt.class))).thenReturn(answer("Please clarify."));
        agent.chat("And now?", null, id, null);
        verifyNoInteractions(embedding);
        verify(model).call(prompts.capture());
        assertThat(prompts.getValue().getInstructions()).noneSatisfy(m -> assertThat(m.getText()).contains("RETRIEVED PROCEDURES"));
    }

    @Test
    void providerFailureDoesNotCallChatModel() {
        doThrow(new IllegalStateException("provider unavailable")).when(embedding).embed(any(Document.class));
        var answers = new KnowledgeAnswerService(ChatClient.builder(model));
        var session = rag.prepare(new RagSelection("battery", 3, 0, null, null));
        assertThatThrownBy(() -> answers.answer("battery", session)).isInstanceOf(RuntimeException.class);
        verify(model, never()).call(any(Prompt.class));
    }
}

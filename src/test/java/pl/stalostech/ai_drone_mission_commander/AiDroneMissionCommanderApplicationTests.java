package pl.stalostech.ai_drone_mission_commander;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiDroneMissionCommanderApplicationTests {

    private static final String QUESTION =
            "What should a drone operator check before an inspection mission?";
    private static final String ANSWER = "Check battery, weather, GPS and the inspection area.";
    private static final String PROVIDER_RESPONSE = """
            {
              "id": "chatcmpl-test",
              "object": "chat.completion",
              "created": 1700000000,
              "model": "test-model",
              "choices": [{
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "Check battery, weather, GPS and the inspection area."
                },
                "finish_reason": "stop"
              }],
              "usage": {"prompt_tokens": 20, "completion_tokens": 12, "total_tokens": 32}
            }
            """;
    private static final BlockingQueue<RecordedRequest> REQUESTS = new LinkedBlockingQueue<>();
    private static final AtomicReference<String> RESPONSE = new AtomicReference<>(PROVIDER_RESPONSE);
    private static final AtomicReference<String> TOOL_RESPONSE = new AtomicReference<>();
    private static final AtomicInteger PROVIDER_STATUS = new AtomicInteger(200);
    private static final AtomicBoolean STREAM_RESPONSE = new AtomicBoolean();
    private static final AtomicReference<String> STREAM_BODY = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> STREAM_REST = new AtomicReference<>(new CountDownLatch(0));
    private static final HttpServer PROVIDER = startProvider();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void configureLocalProvider(DynamicPropertyRegistry registry) {
        // Override even credentials inherited from the developer's environment.
        registry.add("spring.ai.model.chat", () -> "openai");
        registry.add("spring.ai.openai.api-key", () -> "test-only-not-a-real-key");
        registry.add("spring.ai.openai.base-url",
                () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/v1");
        registry.add("spring.ai.openai.chat.api-key", () -> "test-only-not-a-real-key");
        registry.add("spring.ai.openai.chat.base-url",
                () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/v1");
        registry.add("spring.ai.openai.chat.options.model", () -> "test-model");
        // Keep production retry behavior unchanged; these tests exercise a single attempt.
        registry.add("spring.ai.openai.max-retries", () -> 0);
        registry.add("spring.ai.openai.chat.max-retries", () -> 0);
        registry.add("spring.ai.openai.timeout", () -> "2s");
        registry.add("spring.ai.openai.chat.timeout", () -> "2s");
    }

    @BeforeEach
    void resetProvider() {
        REQUESTS.clear();
        RESPONSE.set(PROVIDER_RESPONSE);
        TOOL_RESPONSE.set(null);
        PROVIDER_STATUS.set(200);
        STREAM_RESPONSE.set(false);
        STREAM_BODY.set(null);
        STREAM_REST.set(new CountDownLatch(0));
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.stop(0);
    }

    @Test
    void contextLoads() {
        assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
        assertThat(context.getBean(ChatModel.class)).isInstanceOf(OpenAiChatModel.class);
        ChatClient.Builder builder = context.getBean(ChatClient.Builder.class);
        assertThat(builder.build()).isNotNull();
        assertThat(context.getBean(ChatClient.Builder.class)).isNotSameAs(builder);
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void servesSwaggerUiAndItsLocalApiDefinition() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        var page = mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk()).andReturn();
        assertThat(page.getResponse().getContentAsString()).contains("Swagger UI", "swagger-ui-bundle.js");
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        var initializer = mvc.perform(get("/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk()).andReturn();
        assertThat(initializer.getResponse().getContentAsString()).contains("/v3/api-docs/swagger-config");
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
        assertThat(REQUESTS).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/chat", "/api/chat/model"})
    void documentsChatContractAndProvidesAnExecutableExample(String endpoint) throws Exception {
        var result = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Drone Mission AI"))
                .andExpect(jsonPath("$.components.schemas.ChatRequest.required[0]").value("message"))
                .andExpect(jsonPath("$.components.schemas.ChatRequest.properties.message.type").value("string"))
                .andReturn();
        JsonNode spec = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(spec.path("paths").size()).isEqualTo(21);
        assertThat(spec.at("/components/schemas/AgentChatRequest/properties/conversationId/format").asText())
                .isEqualTo("uuid");
        assertThat(spec.at("/components/schemas/AgentChatRequest/properties/missionId/type").asText())
                .isEqualTo("string");
        assertThat(spec.at("/components/schemas/AgentChatReply/properties/conversationId/format").asText())
                .isEqualTo("uuid");
        JsonNode streaming = spec.path("paths").path("/api/chat/stream").path("get");
        assertThat(streaming.at("/responses/200/content/text~1event-stream").isMissingNode()).isFalse();
        assertThat(streaming.path("parameters").size()).isEqualTo(3);
        JsonNode operation = spec.path("paths").path(endpoint).path("post");
        assertThat(operation.path("summary").asText()).isNotBlank();
        assertThat(operation.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ChatRequest");
        assertThat(operation.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ChatReply");
        for (String code : new String[]{"400", "502", "503"}) {
            assertThat(operation.path("responses").path(code).path("description").asText()).isNotBlank();
        }
        String example = spec.at("/components/schemas/ChatRequest/properties/message/example").asText();
        assertThat(example).isEqualTo(QUESTION);
        assertThat(REQUESTS).isEmpty();
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Message(example))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(ANSWER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/chat", "/api/chat/model"})
    void answersPreflightQuestionAndExposesProviderMetadata(String endpoint) throws Exception {
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Message(QUESTION))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(ANSWER))
                .andExpect(jsonPath("$.metadata.id").value("chatcmpl-test"))
                .andExpect(jsonPath("$.metadata.model").value("test-model"))
                .andExpect(jsonPath("$.metadata.finishReason").value("STOP"))
                .andExpect(jsonPath("$.metadata.promptTokens").value(20))
                .andExpect(jsonPath("$.metadata.completionTokens").value(12))
                .andExpect(jsonPath("$.metadata.totalTokens").value(32));

        RecordedRequest request = REQUESTS.poll(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.authorization()).isEqualTo("Bearer test-only-not-a-real-key");
        JsonNode body = objectMapper.readTree(request.body());
        assertThat(body.path("model").asText()).isEqualTo("test-model");
        assertThat(body.path("messages").size()).isEqualTo(2);
        assertThat(body.at("/messages/0/role").asText()).isEqualTo("system");
        assertThat(body.at("/messages/0/content").asText()).contains("cannot execute drone missions");
        assertThat(body.at("/messages/1/role").asText()).isEqualTo("user");
        assertThat(body.at("/messages/1/content").asText()).isEqualTo(QUESTION);
        assertThat(REQUESTS).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, -1})
    void handlesRealSdkRateLimitAndConnectionFailureWithoutRetryDelays(int providerStatus) throws Exception {
        PROVIDER_STATUS.set(providerStatus);
        RESPONSE.set("""
                {"error":{"message":"sensitive-provider-detail","code":"rate_limit_exceeded"}}
                """);
        var result = mvc.perform(post("/api/chat/model").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Message(QUESTION))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("AI provider error"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("sensitive-provider-detail");
        assertThat(REQUESTS).isNotEmpty();
        if (providerStatus == 429) {
            assertThat(REQUESTS).hasSize(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/chat", "/api/chat/model"})
    void requestOptionsOverrideDefaultsWithoutLeakingIntoLaterRequests(String endpoint) throws Exception {
        for (String options : new String[]{"{\"model\":\"request-model\",\"maxCompletionTokens\":128}",
                "{\"maxCompletionTokens\":64}", "null"}) {
            mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"Hi\",\"options\":" + options + "}"))
                    .andExpect(status().isOk());
            var sent = REQUESTS.poll(1, TimeUnit.SECONDS);
            assertThat(sent).isNotNull();
            var body = objectMapper.readTree(sent.body());
            assertThat(body.path("model").asText()).isEqualTo(options.contains("request-model") ? "request-model" : "test-model");
            assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(
                    options.equals("null") ? 2048 : options.contains("128") ? 128 : 64);
            assertThat(body.path("temperature").isMissingNode()).isTrue();
        }
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void encodesStreamingProviderRateLimitAsASanitizedEvent(
            org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        PROVIDER_STATUS.set(429);
        RESPONSE.set("""
                {"error":{"message":"sensitive-provider-detail","code":"rate_limit_exceeded"}}
                """);
        var pending = mvc.perform(get("/api/chat/stream").param("message", QUESTION))
                .andExpect(request().asyncStarted()).andReturn();
        pending.getAsyncResult(5000);
        var result = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("event:error", "\"status\":503")
                .doesNotContain("event:done", "event:delta", "sensitive-provider-detail");
        assertThat(REQUESTS).hasSize(1);
        assertThat(output.getAll()).contains("providerStatus=429").doesNotContain("sensitive-provider-detail");
    }

    @Test
    void streamsFirstFragmentBeforeProviderCompletes() throws Exception {
        STREAM_RESPONSE.set(true);
        var release = new CountDownLatch(1);
        STREAM_REST.set(release);
        var pending = mvc.perform(get("/api/chat/stream").param("message", QUESTION)
                        .param("maxCompletionTokens", "64"))
                .andExpect(request().asyncStarted()).andReturn();
        try {
            await().atMost(java.time.Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(pending.getResponse().getContentAsString()).contains("Check "));
            assertThat(pending.getResponse().getContentAsString()).doesNotContain("event:done", "battery.");
        } finally {
            release.countDown();
        }
        pending.getAsyncResult(5000);
        var result = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("event:delta", "Check ", "battery.", "event:done")
                .doesNotContain("event:error");
        var sent = REQUESTS.poll(1, TimeUnit.SECONDS);
        assertThat(sent).isNotNull();
        var body = objectMapper.readTree(sent.body());
        assertThat(body.path("stream").asBoolean()).isTrue();
        assertThat(body.path("model").asText()).isEqualTo("test-model");
        assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(64);
        assertThat(body.at("/messages/0/content").asText()).contains("Never claim that an action has been executed");
        assertThat(REQUESTS).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"truncated", "missing-finish", "whitespace"})
    void rejectsIncompleteOrBlankProviderStreams(String scenario) throws Exception {
        STREAM_RESPONSE.set(true);
        STREAM_BODY.set(switch (scenario) {
            case "truncated" -> streamChunk("Partial answer");
            case "missing-finish" -> streamChunk("Partial answer") + "data: [DONE]\n\n";
            default -> streamChunk("   ") + streamFinish();
        });
        var pending = mvc.perform(get("/api/chat/stream").param("message", QUESTION))
                .andExpect(request().asyncStarted()).andReturn();
        pending.getAsyncResult(5000);
        var result = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("event:delta", "event:error", "\"status\":502")
                .doesNotContain("event:done");
        assertThat(REQUESTS).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void extractsIntentAndSendsSchemaInTheSelectedMode(boolean nativeOutput) throws Exception {
        missionResponse("""
                {"droneId":"Alpha","type":"INSPECTION","targetSector":"Bravo","returnHome":true}
                """);
        mvc.perform(post("/api/missions/intent").param("nativeOutput", String.valueOf(nativeOutput))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Send Alpha to Bravo, inspect the area and return home.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.droneId").value("alpha"))
                .andExpect(jsonPath("$.type").value("INSPECTION"))
                .andExpect(jsonPath("$.targetSector").value("BRAVO"))
                .andExpect(jsonPath("$.returnHome").value(true));
        var sent = REQUESTS.poll(1, TimeUnit.SECONDS);
        assertThat(sent).isNotNull();
        var body = objectMapper.readTree(sent.body());
        assertThat(body.path("model").asText()).isEqualTo("test-model");
        if (nativeOutput) {
            assertThat(body.at("/response_format/type").asText()).isEqualTo("json_schema");
            assertThat(body.at("/response_format/json_schema/strict").asBoolean()).isTrue();
            var schema = body.at("/response_format/json_schema/schema");
            assertThat(schema.path("properties").size()).isEqualTo(4);
            assertThat(schema.path("required").size()).isEqualTo(4);
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
            for (String field : new String[]{"droneId", "type", "targetSector"}) {
                assertThat(schema.path("properties").path(field).toString()).contains("null");
            }
        } else {
            assertThat(body.path("response_format").isMissingNode()).isTrue();
            assertThat(body.path("messages").toString()).contains("returnHome", "INSPECTION", "properties");
        }
        assertThat(REQUESTS).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void rejectsInvalidIntentWithoutLeakingModelText(boolean nativeOutput,
            org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        missionResponse("{\"droneId\":\"sensitive-model-output\",\"type\":\"UNSUPPORTED\"}");
        var result = mvc.perform(post("/api/missions/intent").param("nativeOutput", String.valueOf(nativeOutput))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Inspect Bravo\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Invalid AI output")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("sensitive-model-output", "UNSUPPORTED");
        assertThat(output.getAll()).doesNotContain("sensitive-model-output");
        assertThat(REQUESTS).hasSize(1);
    }

    @Test
    void documentsIntentExtractionWithoutCallingProvider() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var spec = objectMapper.readTree(result.getResponse().getContentAsString());
        var operation = spec.path("paths").path("/api/missions/intent").path("post");
        assertThat(operation.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/MissionIntent");
        assertThat(operation.path("parameters").get(0).path("name").asText()).isEqualTo("nativeOutput");
        assertThat(REQUESTS).isEmpty();
    }

    private void missionResponse(String content) throws Exception {
        var body = objectMapper.readTree(PROVIDER_RESPONSE);
        ((tools.jackson.databind.node.ObjectNode) body.at("/choices/0/message")).put("content", content);
        RESPONSE.set(objectMapper.writeValueAsString(body));
    }

    @Test
    void agentUsesLiveToolResultsThroughTheRealOpenAiClient() throws Exception {
        var world = context.getBean(pl.stalostech.ai_drone_mission_commander.simulation.DroneWorld.class);
        world.reset();
        context.getBean(pl.stalostech.ai_drone_mission_commander.simulation.SimulationEventService.class)
                .inject(pl.stalostech.ai_drone_mission_commander.domain.SimulationEventType.BATTERY_DROP, "alpha", 20);
        var before = world.snapshot();
        var calls = objectMapper.createArrayNode();
        addToolCall(calls, "getDroneStatus", "{\"droneId\":\"alpha\"}");
        addToolCall(calls, "getWeather", "{}");
        addToolCall(calls, "getSector", "{\"sectorId\":\"SECTOR_B\"}");
        addToolCall(calls, "calculateRoute", "{\"droneId\":\"alpha\",\"sectorId\":\"SECTOR_B\",\"returnHome\":true}");
        setToolResponse(calls);
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Can Alpha inspect SECTOR_B and return home?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(ANSWER))
                .andExpect(jsonPath("$.metadata.totalTokens").value(64));
        var first = objectMapper.readTree(REQUESTS.poll(2, TimeUnit.SECONDS).body());
        assertThat(first.path("tools").size()).isEqualTo(7);
        var second = objectMapper.readTree(REQUESTS.poll(2, TimeUnit.SECONDS).body());
        var results = new java.util.ArrayList<JsonNode>();
        second.path("messages").forEach(message -> {
            if (message.path("role").asText().equals("tool")) {
                results.add(objectMapper.readTree(message.path("content").asText()));
            }
        });
        assertThat(results).hasSize(4);
        assertThat(results.get(0).path("batteryPercent").asInt()).isEqualTo(62);
        assertThat(results.get(1).path("windKmh").asInt()).isEqualTo(12);
        assertThat(results.get(2).path("id").asText()).isEqualTo("SECTOR_B");
        assertThat(results.get(3).path("estimatedBatteryUsage").asInt()).isEqualTo(20);
        assertThat(world.snapshot()).isEqualTo(before);
        assertThat(REQUESTS).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"droneId\":\"missing-drone\"}", "{invalid"})
    void agentFeedsSanitizedToolFailuresBackToTheModel(String arguments) throws Exception {
        var calls = objectMapper.createArrayNode();
        addToolCall(calls, "getDroneStatus", arguments);
        setToolResponse(calls);
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Check this drone\"}")).andExpect(status().isOk());
        REQUESTS.poll(2, TimeUnit.SECONDS);
        var second = objectMapper.readTree(REQUESTS.poll(2, TimeUnit.SECONDS).body());
        var toolMessage = java.util.stream.StreamSupport.stream(second.path("messages").spliterator(), false)
                .filter(message -> message.path("role").asText().equals("tool")).findFirst().orElseThrow();
        assertThat(toolMessage.path("content").asText())
                .contains(arguments.startsWith("{invalid") ? "INVALID_ARGUMENTS" : "NOT_FOUND")
                .doesNotContain("missing-drone", "Exception", "{invalid");
    }

    @Test
    void agentCannotResolveAnExecutionTool() throws Exception {
        var world = context.getBean(pl.stalostech.ai_drone_mission_commander.simulation.DroneWorld.class);
        var before = world.snapshot();
        var calls = objectMapper.createArrayNode();
        addToolCall(calls, "executeMission", "{\"missionId\":\"mission-1\"}");
        setToolResponse(calls);
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Execute a mission\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.title").value("Agent tool error"));
        assertThat(world.snapshot()).isEqualTo(before);
        assertThat(REQUESTS).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"private-tool-name-marker", "unknown-tool\nFORGED_LOG_MARKER"})
    @ExtendWith(OutputCaptureExtension.class)
    void unknownToolNamesNeverAppearInLogsOrPublicErrors(String toolName, CapturedOutput output) throws Exception {
        var calls = objectMapper.createArrayNode();
        addToolCall(calls, toolName, "{}");
        setToolResponse(calls);
        var result = mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Read the simulated world\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Agent tool error"))
                .andReturn();
        assertThat(output.getAll())
                .contains("Agent tool failure: code=TOOL_ORCHESTRATION_FAILED")
                .doesNotContain(toolName, "private-tool-name-marker", "FORGED_LOG_MARKER",
                        "LLM may have adapted", "IllegalStateException");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("private-tool-name-marker", "FORGED_LOG_MARKER", "IllegalStateException");
        assertThat(REQUESTS).hasSize(1);
    }

    @Test
    void exposesToolDefinitionsWithoutCallingProviderAndValidatesChatInput() throws Exception {
        mvc.perform(get("/api/agent/tools")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\" \"}")).andExpect(status().isBadRequest());
        assertThat(REQUESTS).isEmpty();
    }

    private void addToolCall(tools.jackson.databind.node.ArrayNode calls, String name, String arguments) {
        var call = calls.addObject();
        call.put("id", "call-" + calls.size()).put("type", "function");
        call.putObject("function").put("name", name).put("arguments", arguments);
    }

    @Test
    void conversationsKeepReferencesSeparateAndReadFreshTelemetryThroughTools() throws Exception {
        var world = context.getBean(pl.stalostech.ai_drone_mission_commander.simulation.DroneWorld.class);
        world.reset();
        var alpha = java.util.UUID.randomUUID();
        var charlie = java.util.UUID.randomUUID();
        memoryChat(alpha, "We are monitoring Alpha.");
        REQUESTS.poll(2, TimeUnit.SECONDS);
        memoryChat(charlie, "We are monitoring Charlie.");
        REQUESTS.poll(2, TimeUnit.SECONDS);
        context.getBean(pl.stalostech.ai_drone_mission_commander.simulation.SimulationEventService.class)
                .inject(pl.stalostech.ai_drone_mission_commander.domain.SimulationEventType.BATTERY_DROP, "alpha", 20);
        var calls = objectMapper.createArrayNode();
        addToolCall(calls, "getDroneStatus", "{\"droneId\":\"alpha\"}");
        setToolResponse(calls);
        memoryChat(alpha, "How much battery does it have?");
        var sent = REQUESTS.poll(2, TimeUnit.SECONDS).body();
        assertThat(sent).contains("We are monitoring Alpha.").doesNotContain("We are monitoring Charlie.");
        var withTools = objectMapper.readTree(REQUESTS.poll(2, TimeUnit.SECONDS).body());
        var toolResult = java.util.stream.StreamSupport.stream(withTools.path("messages").spliterator(), false)
                .filter(message -> message.path("role").asText().equals("tool")).findFirst().orElseThrow();
        assertThat(objectMapper.readTree(toolResult.path("content").asText()).path("batteryPercent").asInt()).isEqualTo(62);
        memoryChat(charlie, "How much battery does it have?");
        assertThat(REQUESTS.poll(2, TimeUnit.SECONDS).body())
                .contains("We are monitoring Charlie.").doesNotContain("We are monitoring Alpha.");
        var stored = mvc.perform(get("/api/agent/conversations/{id}/messages", alpha))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[1].role").value("assistant")).andReturn();
        assertThat(stored.getResponse().getContentAsString()).doesNotContain("APPLICATION CONTEXT", "batteryPercent", "tool_calls");
        world.reset();
        mvc.perform(get("/api/agent/conversations/{id}/messages", alpha))
                .andExpect(jsonPath("$.length()").value(4));
        var before = world.snapshot();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/agent/conversations/{id}/messages", alpha)).andExpect(status().isNoContent());
        mvc.perform(get("/api/agent/conversations/{id}/messages", alpha)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/agent/conversations/{id}/messages", charlie)).andExpect(jsonPath("$.length()").value(4));
        assertThat(world.snapshot()).isEqualTo(before);
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void failedProviderTurnDoesNotChangeStoredHistory() throws Exception {
        var id = java.util.UUID.randomUUID();
        memoryChat(id, "Monitor Alpha");
        REQUESTS.poll(2, TimeUnit.SECONDS);
        PROVIDER_STATUS.set(503);
        RESPONSE.set("{\"error\":{\"message\":\"Unavailable\",\"type\":\"server_error\"}}");
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("message", "Failed question", "conversationId", id))))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/agent/conversations/{id}/messages", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("Monitor Alpha"));
    }

    private void memoryChat(java.util.UUID id, String message) throws Exception {
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("message", message, "conversationId", id))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversationId").value(id.toString()));
    }

    @Test
    void agentExposesConversationIdAndSendsSelectedMissionContext() throws Exception {
        var world = context.getBean(pl.stalostech.ai_drone_mission_commander.simulation.DroneWorld.class);
        world.reset();
        var mission = context.getBean(pl.stalostech.ai_drone_mission_commander.simulation.MissionSimulationService.class)
                .create(new pl.stalostech.ai_drone_mission_commander.domain.MissionIntent("alpha",
                        pl.stalostech.ai_drone_mission_commander.domain.MissionType.INSPECTION, "SECTOR_B", true));
        var conversation = java.util.UUID.randomUUID().toString();
        var body = objectMapper.createObjectNode().put("message", "Read the selected mission")
                .put("conversationId", conversation).put("missionId", mission.id());
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversationId").value(conversation));
        var sent = objectMapper.readTree(REQUESTS.poll(2, TimeUnit.SECONDS).body());
        var contextMessages = java.util.stream.StreamSupport.stream(sent.path("messages").spliterator(), false)
                .filter(message -> message.path("role").asText().equals("system"))
                .map(message -> message.path("content").asText())
                .filter(message -> message.startsWith("APPLICATION CONTEXT")).toList();
        assertThat(contextMessages).hasSize(1);
        assertThat(contextMessages.getFirst()).contains(mission.id(), "SECTOR_B", "CREATED")
                .doesNotContain(conversation);
        body.remove("missionId");
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversationId").value(conversation));
        var next = REQUESTS.poll(2, TimeUnit.SECONDS).body();
        assertThat(next).doesNotContain(mission.id());
        var generated = mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Read Alpha\"}")).andExpect(status().isOk()).andReturn();
        var id = objectMapper.readTree(generated.getResponse().getContentAsString()).path("conversationId").asText();
        assertThat(java.util.UUID.fromString(id).toString()).isEqualTo(id).isNotEqualTo(conversation);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"message\":\"x\",\"conversationId\":\"not-a-uuid\"}",
            "{\"message\":\"x\",\"conversationId\":123}",
            "{\"message\":\"x\",\"missionId\":123}",
            "{\"message\":\"x\",\"missionId\":\" \"}"
    })
    void rejectsInvalidAgentContextBeforeCallingProvider(String body) throws Exception {
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void missingMissionContextReturns404WithoutCallingProvider() throws Exception {
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Read mission\",\"missionId\":\"missing-private-mission\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.title").value("Mission not found"));
        assertThat(REQUESTS).isEmpty();
    }

    private void setToolResponse(tools.jackson.databind.node.ArrayNode calls) {
        var response = objectMapper.readTree(PROVIDER_RESPONSE);
        var choice = (tools.jackson.databind.node.ObjectNode) response.path("choices").get(0);
        choice.put("finish_reason", "tool_calls");
        var message = (tools.jackson.databind.node.ObjectNode) choice.path("message");
        message.putNull("content");
        message.set("tool_calls", calls);
        TOOL_RESPONSE.set(objectMapper.writeValueAsString(response));
    }

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    REQUESTS.add(new RecordedRequest(exchange.getRequestMethod(),
                            exchange.getRequestURI().getPath(),
                            exchange.getRequestHeaders().getFirst("Authorization"),
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                    if (PROVIDER_STATUS.get() == -1) {
                        // Simulate a dropped connection, including any SDK retry attempts.
                        return;
                    }
                    if (STREAM_RESPONSE.get() && PROVIDER_STATUS.get() == 200) {
                        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                        exchange.sendResponseHeaders(200, 0);
                        if (STREAM_BODY.get() != null) {
                            exchange.getResponseBody().write(STREAM_BODY.get().getBytes(StandardCharsets.UTF_8));
                            return;
                        }
                        exchange.getResponseBody().write(streamChunk("Check ").getBytes(StandardCharsets.UTF_8));
                        exchange.getResponseBody().flush();
                        try {
                            if (!STREAM_REST.get().await(3, TimeUnit.SECONDS)) {
                                return;
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        exchange.getResponseBody().write((streamChunk("battery.") + streamFinish()).getBytes(StandardCharsets.UTF_8));
                        return;
                    }
                    String toolResponse = TOOL_RESPONSE.getAndSet(null);
                    byte[] body = (toolResponse == null ? RESPONSE.get() : toolResponse).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(PROVIDER_STATUS.get(), body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();
            return server;
        }
        catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Message(String message) {
    }

    private static String streamChunk(String text) {
        return "data: {\"id\":\"stream-test\",\"object\":\"chat.completion.chunk\",\"created\":1700000000,"
                + "\"model\":\"test-model\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\","
                + "\"content\":\"" + text + "\"},\"finish_reason\":null}]}\n\n";
    }

    private static String streamFinish() {
        return streamChunk("").replace("\"finish_reason\":null", "\"finish_reason\":\"stop\"")
                + "data: [DONE]\n\n";
    }

    private record RecordedRequest(String method, String path, String authorization, String body) {
    }
}

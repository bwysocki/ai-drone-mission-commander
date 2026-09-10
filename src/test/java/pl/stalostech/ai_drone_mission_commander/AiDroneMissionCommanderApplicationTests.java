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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
        assertThat(spec.path("paths").size()).isEqualTo(18);
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
                    byte[] body = RESPONSE.get().getBytes(StandardCharsets.UTF_8);
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

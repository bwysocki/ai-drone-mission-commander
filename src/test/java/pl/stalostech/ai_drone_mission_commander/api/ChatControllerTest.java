package pl.stalostech.ai_drone_mission_commander.api;

import java.util.List;
import java.util.stream.Stream;

import com.openai.errors.OpenAIIoException;
import com.openai.core.http.Headers;
import com.openai.errors.UnauthorizedException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.stalostech.ai_drone_mission_commander.agent.ChatService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ChatService service;

    @ParameterizedTest
    @ValueSource(strings = {"/api/chat", "/api/chat/model"})
    void routesValidRequestToTheSelectedServiceMethod(String endpoint) throws Exception {
        var response = new ChatResponse(List.of(new Generation(new AssistantMessage("Check battery and weather."))));
        if (endpoint.endsWith("/model")) {
            when(service.chatWithModel("Check Alpha")).thenReturn(response);
        } else {
            when(service.chat("Check Alpha")).thenReturn(response);
        }
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Check Alpha\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Check battery and weather."));
        if (endpoint.endsWith("/model")) {
            verify(service).chatWithModel("Check Alpha");
        } else {
            verify(service).chat("Check Alpha");
        }
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidInputBeforeCallingService(String endpoint, String body) throws Exception {
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of("/api/chat", "/api/chat/model").flatMap(endpoint ->
                Stream.of("{}", "{\"message\":null}", "{\"message\":\"\"}",
                                "{\"message\":\"   \"}", "null", "{", "",
                                "{\"message\":42}", "{\"message\":1.5}", "{\"message\":true}",
                                "{\"message\":false}", "{\"message\":[]}", "{\"message\":{}}")
                        .map(body -> Arguments.of(endpoint, body)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/chat", "/api/chat/model"})
    void returnsBadGatewayWhenThereIsNoAnswer(String endpoint) throws Exception {
        var emptyResponse = new ChatResponse(List.of());
        if (endpoint.endsWith("/model")) {
            when(service.chatWithModel("Check Alpha")).thenReturn(emptyResponse);
        } else {
            when(service.chat("Check Alpha")).thenReturn(emptyResponse);
        }
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Check Alpha\"}"))
                .andExpect(status().isBadGateway());
    }

    @ParameterizedTest
    @MethodSource("providerErrors")
    void translatesProviderExceptionsIntoSanitizedHttpResponses(String endpoint, int statusCode) throws Exception {
        RuntimeException error = statusCode == 503
                ? new OpenAIIoException("sensitive-provider-detail")
                : UnauthorizedException.builder().headers(Headers.builder().build()).build();
        if (endpoint.endsWith("/model")) {
            when(service.chatWithModel("Check Alpha")).thenThrow(error);
        } else {
            when(service.chat("Check Alpha")).thenThrow(error);
        }
        var result = mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Check Alpha\"}"))
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.title").value("AI provider error"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("sensitive-provider-detail", "com.openai");
    }

    static Stream<Arguments> providerErrors() {
        return Stream.of("/api/chat", "/api/chat/model").flatMap(endpoint ->
                Stream.of(502, 503).map(code -> Arguments.of(endpoint, code)));
    }
}

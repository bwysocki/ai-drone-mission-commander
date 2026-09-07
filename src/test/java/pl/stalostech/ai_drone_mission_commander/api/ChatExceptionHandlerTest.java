package pl.stalostech.ai_drone_mission_commander.api;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import com.openai.core.http.Headers;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.openai.models.ErrorObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ChatExceptionHandlerTest {

    private final ChatExceptionHandler handler = new ChatExceptionHandler();

    @ParameterizedTest
    @MethodSource("providerStatuses")
    void classifiesProviderHttpStatuses(int providerStatus, int expectedStatus, CapturedOutput output) {
        var error = UnexpectedStatusCodeException.builder().statusCode(providerStatus)
                .headers(Headers.builder().build()).error(errorBody(null)).build();
        assertProblem(handler.handleProviderError(error), expectedStatus);
        assertThat(output.getOut()).contains("providerStatus=" + providerStatus, "providerCode=not_provided");
        assertThat(output.getAll()).doesNotContain("sensitive-provider-detail");
    }

    static Stream<Arguments> providerStatuses() {
        return Stream.of(Arguments.of(400, 502), Arguments.of(401, 502), Arguments.of(403, 502),
                Arguments.of(429, 503), Arguments.of(500, 503), Arguments.of(503, 503));
    }

    @Test
    void classifiesTransportFailureAndLogsOnlyCauseType(CapturedOutput output) {
        var error = new OpenAIIoException("sensitive-provider-detail", new IOException("secret-connection-detail"));
        assertProblem(handler.handleProviderError(error), 503);
        assertThat(output.getOut()).contains("providerStatus=n/a", "causeType=IOException");
        assertThat(output.getAll()).doesNotContain("sensitive-provider-detail", "secret-connection-detail");
    }

    @Test
    void handlesOtherSdkFailuresWithoutExposingMessage(CapturedOutput output) {
        assertProblem(handler.handleProviderError(new OpenAIException("sensitive-provider-detail")), 502);
        assertThat(output.getAll()).doesNotContain("sensitive-provider-detail");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"insufficient_quota", "rate_limit_exceeded", "credit_balance_exhausted",
            "organization_spend_limit_exceeded", "project_spend_limit_exceeded",
            "organization_usage_limit_exceeded", "slow_down", "server_is_overloaded",
            "invalid_api_key", "model_not_found", "sensitive-provider-detail"})
    void logsOnlyRecognizedCodes(String code, CapturedOutput output) {
        var error = RateLimitException.builder().headers(Headers.builder().build()).error(errorBody(code)).build();
        assertProblem(handler.handleProviderError(error), 503);
        String expectedCode = code == null ? "not_provided"
                : code.equals("sensitive-provider-detail") ? "other" : code;
        assertThat(output.getOut()).contains("providerStatus=429, providerCode=" + expectedCode);
        assertThat(output.getAll()).doesNotContain("sensitive-provider-detail");
    }

    private static ErrorObject errorBody(String code) {
        return ErrorObject.builder().code(Optional.ofNullable(code)).param(Optional.empty())
                .message("sensitive-provider-detail").type("test-error").build();
    }

    private static void assertProblem(ProblemDetail problem, int status) {
        assertThat(problem.getStatus()).isEqualTo(status);
        assertThat(problem.getTitle()).isEqualTo("AI provider error");
        assertThat(problem.getDetail()).isEqualTo(status == 503
                ? "The AI provider is currently unavailable. Please try again later."
                : "The AI provider could not complete the request.");
    }
}

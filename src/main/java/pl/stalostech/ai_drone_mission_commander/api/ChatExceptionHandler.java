package pl.stalostech.ai_drone_mission_commander.api;

import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ChatController.class)
public class ChatExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatExceptionHandler.class);

    @ExceptionHandler(OpenAIException.class)
    public ProblemDetail handleProviderError(OpenAIException exception) {
        log.warn("AI provider failure: providerStatus={}, providerCode={}, exceptionType={}, causeType={}",
                exception instanceof OpenAIServiceException service ? service.statusCode() : "n/a",
                providerCode(exception), exception.getClass().getSimpleName(),
                exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName());
        boolean unavailable = exception instanceof OpenAIIoException
                || (exception instanceof OpenAIServiceException serviceException
                    && (serviceException.statusCode() == 429 || serviceException.statusCode() >= 500));
        HttpStatus status = unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, unavailable
                ? "The AI provider is currently unavailable. Please try again later."
                : "The AI provider could not complete the request.");
        problem.setTitle("AI provider error");
        return problem;
    }

    private static String providerCode(OpenAIException exception) {
        if (!(exception instanceof OpenAIServiceException service)) {
            return "n/a";
        }
        // Only known codes are logged; arbitrary provider fields may contain sensitive data.
        return switch (service.code().orElse("not_provided")) {
            case "insufficient_quota" -> "insufficient_quota";
            case "rate_limit_exceeded" -> "rate_limit_exceeded";
            case "credit_balance_exhausted" -> "credit_balance_exhausted";
            case "organization_spend_limit_exceeded" -> "organization_spend_limit_exceeded";
            case "project_spend_limit_exceeded" -> "project_spend_limit_exceeded";
            case "organization_usage_limit_exceeded" -> "organization_usage_limit_exceeded";
            case "slow_down" -> "slow_down";
            case "server_is_overloaded" -> "server_is_overloaded";
            case "invalid_api_key" -> "invalid_api_key";
            case "model_not_found" -> "model_not_found";
            case "not_provided" -> "not_provided";
            default -> "other";
        };
    }
}

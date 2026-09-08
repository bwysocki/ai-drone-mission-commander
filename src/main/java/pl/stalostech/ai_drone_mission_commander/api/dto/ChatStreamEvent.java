package pl.stalostech.ai_drone_mission_commander.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.ProblemDetail;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatStreamEvent(String text, ProblemDetail error) {
}

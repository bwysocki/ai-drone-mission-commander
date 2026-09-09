package pl.stalostech.ai_drone_mission_commander.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "Drone Mission AI",
        version = "Milestone 3",
        description = "Ask drone operation questions, override model options and compare complete answers with SSE streaming. "
                + "The application provides advice; it does not execute drone missions yet. "
                + "Configure the OpenAI API key on the server before sending chat requests."))
public class OpenApiConfiguration {
}

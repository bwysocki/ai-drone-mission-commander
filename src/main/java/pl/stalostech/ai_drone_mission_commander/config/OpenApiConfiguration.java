package pl.stalostech.ai_drone_mission_commander.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "Drone Mission AI",
        version = "Milestone 5",
        description = "Ask drone operation questions, override model options and compare complete answers with SSE streaming. "
                + "The Simulation endpoints execute deterministic missions in memory. AI endpoints provide advice, extract intent and read live simulated state through tools. "
                + "Use the simulator profile without an API key, or configure OpenAI to enable the AI endpoints."))
public class OpenApiConfiguration {
}

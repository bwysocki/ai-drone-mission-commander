package pl.stalostech.ai_drone_mission_commander.tools;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;
import pl.stalostech.ai_drone_mission_commander.simulation.exception.SimulationNotFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Validate model-supplied arguments before method binding; never return raw exception details. */
final class ValidatedToolCallback implements ToolCallback {
    private static final Logger log = LoggerFactory.getLogger(ValidatedToolCallback.class);
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final ToolCallback delegate;
    private final ToolDefinition definition;
    private final Schema schema;

    ValidatedToolCallback(ToolCallback delegate) {
        this(delegate, delegate.getToolDefinition());
    }

    ValidatedToolCallback(ToolCallback delegate, ToolDefinition definition) {
        this.delegate = delegate;
        this.definition = definition;
        schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(JSON.readTree(definition.inputSchema()));
    }

    @Override public ToolDefinition getToolDefinition() { return definition; }
    @Override public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
    @Override public String call(String input) { return call(input, null); }

    @Override
    public String call(String input, ToolContext context) {
        try {
            var arguments = JSON.readTree(input);
            if (arguments == null || !arguments.isObject() || !schema.validate(arguments).isEmpty()) {
                return error("INVALID_ARGUMENTS");
            }
        } catch (JacksonException | IllegalArgumentException exception) {
            return error("INVALID_ARGUMENTS");
        }
        try {
            return delegate.call(input, context);
        } catch (RuntimeException exception) {
            Throwable cause = exception instanceof ToolExecutionException ? exception.getCause() : exception;
            if (cause instanceof SimulationNotFoundException) return error("NOT_FOUND");
            if (cause instanceof IllegalArgumentException || cause instanceof JacksonException) {
                return error("INVALID_ARGUMENTS");
            }
            return error("TOOL_UNAVAILABLE");
        }
    }

    private String error(String code) {
        log.warn("Read-only tool failure: tool={}, code={}", getToolDefinition().name(), code);
        // Codes and messages are fixed; no arguments or provider/tool exception messages are logged.
        return switch (code) {
            case "NOT_FOUND" -> "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"The requested simulated object does not exist. Ask for a valid identifier.\"}}";
            case "INVALID_ARGUMENTS" -> "{\"error\":{\"code\":\"INVALID_ARGUMENTS\",\"message\":\"Arguments must match the tool schema and allowed values.\"}}";
            default -> "{\"error\":{\"code\":\"TOOL_UNAVAILABLE\",\"message\":\"The tool could not read simulated state. Do not invent a result.\"}}";
        };
    }
}

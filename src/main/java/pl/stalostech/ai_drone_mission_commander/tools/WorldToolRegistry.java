package pl.stalostech.ai_drone_mission_commander.tools;

import java.util.Arrays;
import java.util.List;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import pl.stalostech.ai_drone_mission_commander.simulation.SimulationEventService;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class WorldToolRegistry {
    private final ToolCallback[] callbacks;

    public WorldToolRegistry(DroneTools drones, WeatherTools weather, MissionTools missions) {
        callbacks = Arrays.stream(ToolCallbacks.from(drones, weather, missions))
                .map(WorldToolRegistry::validatedCallback).toArray(ToolCallback[]::new);
    }

    private static ToolCallback validatedCallback(ToolCallback callback) {
        var definition = callback.getToolDefinition();
        if (!definition.name().equals("getRecentAlerts")) return new ValidatedToolCallback(callback);

        // Method parameter descriptions do not generate numeric bounds in Spring AI.
        // Publish and validate the same bounded schema before binding to Java int.
        var json = new JsonMapper();
        var schema = json.readTree(definition.inputSchema());
        ((ObjectNode) schema.at("/properties/limit"))
                .put("minimum", SimulationEventService.MIN_RECENT_LIMIT)
                .put("maximum", SimulationEventService.MAX_RECENT_LIMIT);
        var boundedDefinition = ToolDefinition.builder().name(definition.name())
                .description(definition.description()).inputSchema(json.writeValueAsString(schema)).build();
        return new ValidatedToolCallback(callback, boundedDefinition);
    }

    public ToolCallback[] callbacks() { return callbacks.clone(); }
    public List<ToolDefinition> definitions() {
        return Arrays.stream(callbacks).map(ToolCallback::getToolDefinition).toList();
    }
}

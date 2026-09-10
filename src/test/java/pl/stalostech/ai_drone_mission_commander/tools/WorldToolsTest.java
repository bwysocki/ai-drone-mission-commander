package pl.stalostech.ai_drone_mission_commander.tools;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.stalostech.ai_drone_mission_commander.domain.*;
import pl.stalostech.ai_drone_mission_commander.simulation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class WorldToolsTest {
    private final DroneWorld world = new DroneWorld();
    private final RouteService routes = new RouteService(world);
    private final DroneSimulationService drones = new DroneSimulationService(world, routes);
    private final SectorService sectors = new SectorService(world);
    private final MissionSimulationService missions = new MissionSimulationService(world, routes, drones, sectors);
    private final SimulationEventService events = new SimulationEventService(world);
    private final WorldToolRegistry registry = new WorldToolRegistry(new DroneTools(drones),
            new WeatherTools(new WeatherService(world)), new MissionTools(sectors, routes, missions, events));
    private final JsonMapper json = new JsonMapper();

    private JsonNode call(String name, String arguments) {
        return json.readTree(Arrays.stream(registry.callbacks())
                .filter(tool -> tool.getToolDefinition().name().equals(name)).findFirst().orElseThrow().call(arguments));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "21", "2147483648", "-2147483649",
            "9876543210987654321", "1e100", "1.5"})
    void rejectsInvalidAlertLimitsBeforeBindingWithoutLoggingArguments(String limit, CapturedOutput output) {
        var before = world.snapshot();
        String arguments = "{\"limit\":" + limit + "}";
        assertThat(call("getRecentAlerts", arguments).at("/error/code").asText()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(world.snapshot()).isEqualTo(before);
        assertThat(output.getAll()).doesNotContain(arguments, "Conversion from JSON failed", "Exception");
        if (limit.length() > 5) assertThat(output.getAll()).doesNotContain(limit);
    }

    @Test
    void publishesAlertBoundsAndAcceptsBothEndpoints() {
        var definition = registry.definitions().stream()
                .filter(tool -> tool.name().equals("getRecentAlerts")).findFirst().orElseThrow();
        var schema = json.readTree(definition.inputSchema());
        assertThat(schema.at("/properties/limit/minimum").asInt()).isEqualTo(1);
        assertThat(schema.at("/properties/limit/maximum").asInt()).isEqualTo(20);
        events.inject(SimulationEventType.BATTERY_DROP, "alpha", 1);
        assertThat(call("getRecentAlerts", "{\"limit\":1}").size()).isEqualTo(1);
        assertThat(call("getRecentAlerts", "{\"limit\":20}").size()).isEqualTo(1);
    }

    @Test
    void classifiesJacksonBindingFailuresAsInvalidArguments() {
        var callback = new org.springframework.ai.tool.ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return org.springframework.ai.tool.definition.ToolDefinition.builder().name("testReader")
                        .description("A test binding failure").inputSchema("{\"type\":\"object\"}").build();
            }
            @Override
            public String call(String input) {
                try {
                    json.readValue("9876543210987654321", Integer.class);
                    throw new AssertionError("Expected a binding failure");
                } catch (tools.jackson.core.JacksonException exception) {
                    throw new org.springframework.ai.tool.execution.ToolExecutionException(getToolDefinition(), exception);
                }
            }
        };
        var result = new ValidatedToolCallback(callback).call("{}");
        assertThat(json.readTree(result).at("/error/code").asText()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(result).doesNotContain("9876543210987654321", "Exception");
    }

    @Test
    void exposesOnlySevenReadToolsWithGeneratedSchemas() {
        assertThat(registry.definitions()).extracting(d -> d.name()).containsExactlyInAnyOrder(
                "getDroneStatus", "getFleetStatus", "getWeather", "getSector", "calculateRoute", "getMission", "getRecentAlerts");
        registry.definitions().forEach(definition -> {
            assertThat(definition.description()).isNotBlank();
            assertThat(json.readTree(definition.inputSchema()).path("type").asText()).isEqualTo("object");
        });
        var schema = json.readTree(registry.definitions().stream()
                .filter(d -> d.name().equals("calculateRoute")).findFirst().orElseThrow().inputSchema());
        assertThat(schema.path("required").toString()).contains("droneId", "sectorId", "returnHome");
        assertThat(schema.at("/properties/returnHome/type").asText()).isEqualTo("boolean");
        registry.callbacks()[0] = null;
        assertThat(registry.callbacks()).doesNotContainNull();
    }

    @Test
    void returnsFreshStateAndQuotesWithoutMutations() {
        assertThat(call("getDroneStatus", "{\"droneId\":\"alpha\"}").path("batteryPercent").asInt()).isEqualTo(82);
        events.inject(SimulationEventType.BATTERY_DROP, "alpha", 20);
        var before = world.snapshot();
        assertThat(call("getDroneStatus", "{\"droneId\":\"alpha\"}").path("batteryPercent").asInt()).isEqualTo(62);
        assertThat(call("getFleetStatus", "{}").size()).isEqualTo(3);
        assertThat(call("getWeather", "{}").path("windKmh").asInt()).isEqualTo(12);
        assertThat(call("getSector", "{\"sectorId\":\"SECTOR_B\"}").path("id").asText()).isEqualTo("SECTOR_B");
        assertThat(call("calculateRoute", "{\"droneId\":\"alpha\",\"sectorId\":\"SECTOR_B\",\"returnHome\":true}")
                .path("estimatedBatteryUsage").asInt()).isEqualTo(20);
        assertThat(world.snapshot()).isEqualTo(before);
    }

    @Test
    void readsExistingMissionsAndBoundedNewestFirstHistory() {
        var mission = missions.create(new MissionIntent("alpha", MissionType.INSPECTION, "SECTOR_B", true));
        assertThat(call("getMission", "{\"missionId\":\"" + mission.id() + "\"}").path("state").asText()).isEqualTo("CREATED");
        events.inject(SimulationEventType.BATTERY_DROP, "alpha", 1);
        events.inject(SimulationEventType.GPS_LOST, "alpha", null);
        assertThat(call("getRecentAlerts", "{\"limit\":1}").get(0).path("type").asText()).isEqualTo("GPS_LOST");
        assertThat(call("getRecentAlerts", "{\"limit\":20}").size()).isEqualTo(2);
        assertThat(call("getRecentAlerts", "{\"limit\":0}").at("/error/code").asText()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(call("getRecentAlerts", "{\"limit\":21}").at("/error/code").asText()).isEqualTo("INVALID_ARGUMENTS");
        world.reset();
        assertThat(call("getRecentAlerts", "{\"limit\":20}").isEmpty()).isTrue();
        assertThat(call("getMission", "{\"missionId\":\"" + mission.id() + "\"}").at("/error/code").asText()).isEqualTo("NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "{}", "{\"droneId\":1}", "{\"droneId\":null}",
            "{\"droneId\":\"alpha\",\"unexpected\":true}", "{\"droneId\":\"alpha\",\"droneId\":\"bravo\"}",
            "{\"droneId\":\"alpha\"} {}"})
    void rejectsInvalidArgumentsBeforeMethodBinding(String arguments) {
        var before = world.snapshot();
        assertThat(call("getDroneStatus", arguments).at("/error/code").asText()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(world.snapshot()).isEqualTo(before);
    }

    @Test
    void unexpectedToolFailuresDoNotExposeExceptionDetails() {
        var callback = new org.springframework.ai.tool.ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return org.springframework.ai.tool.definition.ToolDefinition.builder().name("brokenReader")
                        .description("A failing test reader").inputSchema("{\"type\":\"object\"}").build();
            }
            @Override
            public String call(String input) {
                throw new IllegalStateException("private backend detail");
            }
        };
        var result = new ValidatedToolCallback(callback).call("{}");
        assertThat(json.readTree(result).at("/error/code").asText()).isEqualTo("TOOL_UNAVAILABLE");
        assertThat(result).doesNotContain("private backend detail", "Exception");
    }

    @Test
    void rejectsMissingOrCoercedBooleanAndSanitizesLookupErrors() {
        assertThat(call("calculateRoute", "{\"droneId\":\"alpha\",\"sectorId\":\"SECTOR_B\"}")
                .at("/error/code").asText()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(call("calculateRoute", "{\"droneId\":\"alpha\",\"sectorId\":\"SECTOR_B\",\"returnHome\":\"true\"}")
                .at("/error/code").asText()).isEqualTo("INVALID_ARGUMENTS");
        var error = call("getDroneStatus", "{\"droneId\":\"private-identifier\"}");
        assertThat(error.at("/error/code").asText()).isEqualTo("NOT_FOUND");
        assertThat(error.toString()).doesNotContain("private-identifier", "Exception");
    }
}

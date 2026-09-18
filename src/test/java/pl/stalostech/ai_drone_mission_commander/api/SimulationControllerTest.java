package pl.stalostech.ai_drone_mission_commander.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pl.stalostech.ai_drone_mission_commander.agent.ChatService;
import pl.stalostech.ai_drone_mission_commander.agent.MissionIntentService;
import pl.stalostech.ai_drone_mission_commander.simulation.DroneWorld;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.ai.openai.api-key=", "spring.ai.openai.chat.api-key="})
@AutoConfigureMockMvc
@ActiveProfiles("simulator")
class SimulationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired DroneWorld world;
    @Autowired ApplicationContext context;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void reset() { world.reset(); }

    @Test
    void runsStandaloneAndDocumentsSimulatorWithoutAiBeans() throws Exception {
        assertThat(context.getBeansOfType(ChatModel.class)).isEmpty();
        assertThat(context.getBeansOfType(ChatService.class)).isEmpty();
        assertThat(context.getBeansOfType(MissionIntentService.class)).isEmpty();
        assertThat(context.getBeansOfType(pl.stalostech.ai_drone_mission_commander.agent.WorldAgentService.class)).isEmpty();
        assertThat(context.getBeansOfType(pl.stalostech.ai_drone_mission_commander.agent.advisor.MissionContextAdvisor.class)).isEmpty();
        assertThat(context.getBeansOfType(pl.stalostech.ai_drone_mission_commander.agent.advisor.DevelopmentLoggingAdvisor.class)).isEmpty();
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var paths = mapper.readTree(result.getResponse().getContentAsString()).path("paths");
        assertThat(paths.has("/api/simulation/missions")).isTrue();
        assertThat(paths.has("/api/simulation/events")).isTrue();
        assertThat(paths.has("/api/chat")).isFalse();
        assertThat(paths.has("/api/knowledge/search")).isFalse();
        assertThat(paths.has("/api/knowledge/ask")).isFalse();
        assertThat(context.getBeansOfType(pl.stalostech.ai_drone_mission_commander.rag.KnowledgeSearchService.class)).isEmpty();
        assertThat(paths.has("/api/agent/chat")).isFalse();
        assertThat(paths.has("/api/agent/tools")).isFalse();
        assertThat(paths.has("/api/agent/conversations/{conversationId}/messages")).isFalse();
        assertThat(context.getBeansOfType(pl.stalostech.ai_drone_mission_commander.memory.ConversationMemory.class)).isEmpty();
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void exposesInitialStateAndRouteQuote() throws Exception {
        mvc.perform(get("/api/simulation/world")).andExpect(status().isOk())
                .andExpect(jsonPath("$.drones.length()").value(3))
                .andExpect(jsonPath("$.drones[0].batteryPercent").value(82))
                .andExpect(jsonPath("$.weather.windKmh").value(12));
        mvc.perform(get("/api/simulation/routes").param("droneId", "alpha").param("sectorId", "SECTOR_B")
                        .param("returnHome", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.distanceKm").value(6))
                .andExpect(jsonPath("$.estimatedBatteryUsage").value(20));
        mvc.perform(get("/api/simulation/drones")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/simulation/drones/alpha")).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("READY"));
        mvc.perform(get("/api/simulation/weather")).andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("GOOD"));
        mvc.perform(get("/api/simulation/sectors")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/simulation/sectors/SECTOR_A")).andExpect(status().isOk()).andExpect(jsonPath("$.position.xKm").value(2));
    }

    private String createMission() throws Exception {
        var result = mvc.perform(post("/api/simulation/missions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"droneId\":\"Alpha\",\"type\":\"INSPECTION\",\"targetSector\":\"SECTOR_B\",\"returnHome\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CREATED")).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    @Test
    void createsExecutesAndResetsMissionWithoutModelCalls() throws Exception {
        var id = createMission();
        mvc.perform(post("/api/simulation/missions/{id}/execute", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"));
        mvc.perform(get("/api/simulation/missions/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.inspectionResult.anomaly").value(false));
        mvc.perform(get("/api/simulation/missions")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/simulation/drones/alpha")).andExpect(status().isOk())
                .andExpect(jsonPath("$.batteryPercent").value(59)).andExpect(jsonPath("$.position.xKm").value(0));
        mvc.perform(post("/api/simulation/missions/{id}/execute", id)).andExpect(status().isConflict());
        mvc.perform(post("/api/simulation/reset")).andExpect(status().isOk())
                .andExpect(jsonPath("$.missions.length()").value(0)).andExpect(jsonPath("$.drones[0].batteryPercent").value(82));
    }

    @Test
    void eventMakesQueuedMissionFailAndDirectMovementReturnConflict() throws Exception {
        var id = createMission();
        mvc.perform(post("/api/simulation/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GPS_LOST\",\"droneId\":\"alpha\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sequence").value(1));
        mvc.perform(post("/api/simulation/missions/{id}/execute", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));
        mvc.perform(post("/api/simulation/drones/alpha/move").param("sectorId", "SECTOR_A"))
                .andExpect(status().isConflict());
        assertThat(world.drone("alpha").batteryPercent()).isEqualTo(82);
    }

    @Test
    void movesAndReturnsDroneThroughApi() throws Exception {
        mvc.perform(post("/api/simulation/drones/alpha/move").param("sectorId", "SECTOR_A"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.batteryPercent").value(75));
        mvc.perform(post("/api/simulation/drones/alpha/return-home"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.batteryPercent").value(68));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"type\":\"UNKNOWN\"}", "{\"type\":0,\"droneId\":\"alpha\"}",
            "{\"type\":\"BATTERY_DROP\",\"droneId\":\"alpha\",\"ammount\":60}",
            "{\"type\":\"STRONG_WIND\",\"windKmh\":100}",
            "{\"type\":\"GPS_LOST\"}", "{\"type\":\"BATTERY_DROP\",\"droneId\":\"alpha\",\"amount\":0}",
            "{\"type\":\"BATTERY_DROP\",\"droneId\":\"alpha\",\"amount\":1.5}",
            "{\"type\":\"BATTERY_DROP\",\"droneId\":\"alpha\",\"amount\":\"20\"}",
            "{\"type\":\"STRONG_WIND\",\"droneId\":\"alpha\"}"})
    void rejectsInvalidEventsWithoutMutation(String body) throws Exception {
        var before = world.snapshot();
        mvc.perform(post("/api/simulation/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(world.snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"droneId\":\"alpha\",\"type\":\"INSPECTION\",\"targetSector\":\"SECTOR_A\"}",
            "{\"droneId\":\"alpha\",\"type\":\"INSPECTION\",\"targetSector\":\"SECTOR_A\",\"returnHome\":true,\"unexpected\":123}",
            "{\"droneId\":\"alpha\",\"type\":0,\"targetSector\":\"SECTOR_A\",\"returnHome\":true}",
            "{\"droneId\":\"alpha\",\"type\":\"INSPECTION\",\"targetSector\":\"SECTOR_A\",\"returnHome\":null}",
            "{\"droneId\":\"alpha\",\"type\":\"INSPECTION\",\"targetSector\":\"SECTOR_A\",\"returnHome\":\"true\"}",
            "{\"droneId\":\"alpha\",\"type\":\"INSPECTION\",\"targetSector\":\"SECTOR_A\",\"returnHome\":true,\"returnHome\":false}",
            "{\"droneId\":\"alpha\",\"type\":\"UNKNOWN\",\"targetSector\":\"SECTOR_A\",\"returnHome\":true}"})
    void rejectsInvalidMissionInputWithoutMutation(String body) throws Exception {
        var before = world.snapshot();
        mvc.perform(post("/api/simulation/missions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(world.snapshot()).isEqualTo(before);
    }

    @Test
    void resetInvalidatesOldMissionUrlsWithoutMutatingNewMission() throws Exception {
        var oldId = createMission();
        mvc.perform(post("/api/simulation/reset")).andExpect(status().isOk());
        var newId = createMission();
        assertThat(newId).isNotEqualTo(oldId);
        var before = world.snapshot();
        mvc.perform(get("/api/simulation/missions/{id}", oldId)).andExpect(status().isNotFound());
        mvc.perform(post("/api/simulation/missions/{id}/execute", oldId)).andExpect(status().isNotFound());
        assertThat(world.snapshot()).isEqualTo(before);
        mvc.perform(post("/api/simulation/missions/{id}/execute", newId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("COMPLETED"));
    }

    @Test
    void returns404ForUnknownWorldObjects() throws Exception {
        mvc.perform(get("/api/simulation/drones/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/simulation/sectors/missing")).andExpect(status().isNotFound());
        mvc.perform(post("/api/simulation/missions/missing/execute")).andExpect(status().isNotFound());
        mvc.perform(get("/api/simulation/routes").param("droneId", "alpha").param("sectorId", "MISSING"))
                .andExpect(status().isNotFound());
    }
}

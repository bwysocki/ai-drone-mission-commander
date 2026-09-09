package pl.stalostech.ai_drone_mission_commander.api;

import com.openai.errors.OpenAIIoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.stalostech.ai_drone_mission_commander.agent.exception.InvalidMissionOutputException;
import pl.stalostech.ai_drone_mission_commander.agent.MissionIntentService;
import pl.stalostech.ai_drone_mission_commander.domain.MissionIntent;
import pl.stalostech.ai_drone_mission_commander.domain.MissionType;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MissionIntentController.class)
@ActiveProfiles("test")
class MissionIntentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MissionIntentService service;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void returnsTypedIntentAndPassesOutputMode(boolean nativeOutput) throws Exception {
        when(service.extract("Inspect Bravo", nativeOutput))
                .thenReturn(new MissionIntent("alpha", MissionType.INSPECTION, "BRAVO", true));
        mvc.perform(post("/api/missions/intent").param("nativeOutput", String.valueOf(nativeOutput))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Inspect Bravo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.droneId").value("alpha"))
                .andExpect(jsonPath("$.type").value("INSPECTION"))
                .andExpect(jsonPath("$.targetSector").value("BRAVO"))
                .andExpect(jsonPath("$.returnHome").value(true));
        verify(service).extract("Inspect Bravo", nativeOutput);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"message\":null}", "{\"message\":\" \"}",
            "{\"message\":1}", "{\"message\":true}", "{\"message\":[]}", "{\"message\":{}}"})
    void rejectsInvalidInputWithoutCallingTheModel(String body) throws Exception {
        mvc.perform(post("/api/missions/intent").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidMode() throws Exception {
        mvc.perform(post("/api/missions/intent").param("nativeOutput", "invalid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Inspect Bravo\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void mapsInvalidModelOutputTo502() throws Exception {
        when(service.extract("Inspect Bravo", false)).thenThrow(new InvalidMissionOutputException());
        mvc.perform(post("/api/missions/intent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Inspect Bravo\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.title").value("Invalid AI output"));
    }

    @Test
    void keepsExistingProviderFailureContract() throws Exception {
        when(service.extract("Inspect Bravo", false)).thenThrow(new OpenAIIoException("secret"));
        mvc.perform(post("/api/missions/intent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Inspect Bravo\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.title").value("AI provider error"));
    }
}

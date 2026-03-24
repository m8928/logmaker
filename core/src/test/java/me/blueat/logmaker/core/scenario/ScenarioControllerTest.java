package me.blueat.logmaker.core.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.blueat.logmaker.core.model.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScenarioController.class)
class ScenarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ScenarioService scenarioService;

    @Test
    void getScenarios() throws Exception {
        when(scenarioService.getScenarios()).thenReturn(Collections.singletonList(new ScenarioDto()));

        mockMvc.perform(get("/api/v1/scenario"))
                .andExpect(status().isOk());
    }

    @Test
    void createScenario() throws Exception {
        ScenarioDto scenarioDto = new ScenarioDto();
        scenarioDto.setName("testScenario");
        when(scenarioService.createScenario(any(ScenarioDto.class)))
                .thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        mockMvc.perform(post("/api/v1/scenario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(scenarioDto)))
                .andExpect(status().isOk());
    }

    @Test
    void updateScenario() throws Exception {
        ScenarioDto scenarioDto = new ScenarioDto();
        scenarioDto.setName("testScenario");
        when(scenarioService.updateScenario(eq("testScenario"), any(ScenarioDto.class)))
                .thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        mockMvc.perform(put("/api/v1/scenario/testScenario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(scenarioDto)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteScenario() throws Exception {
        when(scenarioService.deleteScenario("testScenario"))
                .thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        mockMvc.perform(delete("/api/v1/scenario/testScenario"))
                .andExpect(status().isOk());
    }

    @Test
    void startScenario() throws Exception {
        when(scenarioService.startScenario("testScenario"))
                .thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Scenario started"));

        mockMvc.perform(post("/api/v1/scenario/testScenario:start"))
                .andExpect(status().isOk());
    }

    @Test
    void stopScenario() throws Exception {
        when(scenarioService.stopScenario("testScenario"))
                .thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Scenario stopped"));

        mockMvc.perform(post("/api/v1/scenario/testScenario:stop"))
                .andExpect(status().isOk());
    }
}

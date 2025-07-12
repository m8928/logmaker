package me.blueat.logmaker.core.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void getDashboard() throws Exception {
        // Given
        DashboardDto dashboardDto = DashboardDto.builder()
                .maker(1)
                .log(2)
                .sender(3)
                .plugin(4)
                .eps(100L)
                .actualEps(50L)
                .cpu(50.0)
                .memory(1024L)
                .thread(10)
                .build();
        when(dashboardService.getDashboard()).thenReturn(dashboardDto);

        // When & Then
        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maker").value(1))
                .andExpect(jsonPath("$.log").value(2))
                .andExpect(jsonPath("$.sender").value(3))
                .andExpect(jsonPath("$.plugin").value(4))
                .andExpect(jsonPath("$.eps").value(100L))
                .andExpect(jsonPath("$.actualEps").value(50L))
                .andExpect(jsonPath("$.cpu").value(50.0))
                .andExpect(jsonPath("$.memory").value(1024L))
                .andExpect(jsonPath("$.thread").value(10));
    }
}
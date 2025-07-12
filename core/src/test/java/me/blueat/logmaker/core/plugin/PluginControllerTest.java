package me.blueat.logmaker.core.plugin;

import me.blueat.logmaker.core.model.PluginDto;
import me.blueat.logmaker.core.model.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PluginController.class)
class PluginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PluginService pluginService;

    @Test
    void getPlugin() throws Exception {
        // Given
        when(pluginService.getPlugin()).thenReturn(Collections.singletonList(new PluginDto()));

        // When & Then
        mockMvc.perform(get("/api/v1/plugin"))
                .andExpect(status().isOk());
    }

    @Test
    void deletePlugin() throws Exception {
        // Given
        when(pluginService.deletePlugin(any(String.class))).thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        // When & Then
        mockMvc.perform(delete("/api/v1/plugin/testPlugin"))
                .andExpect(status().isOk());
    }

    @Test
    void uploadPlugin() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile("file", "test.jar", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[0]);
        when(pluginService.uploadPlugin(any())).thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        // When & Then
        mockMvc.perform(multipart("/api/v1/plugin").file(file))
                .andExpect(status().isOk());
    }
}
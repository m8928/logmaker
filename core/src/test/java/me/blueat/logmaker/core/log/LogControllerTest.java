package me.blueat.logmaker.core.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.model.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LogController.class)
class LogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LogService logService;

    @Test
    void getLog() throws Exception {
        // Given
        when(logService.getLog()).thenReturn(Collections.singletonList(new LogDto()));

        // When & Then
        mockMvc.perform(get("/api/v1/log"))
                .andExpect(status().isOk());
    }

    @Test
    void createLog() throws Exception {
        // Given
        LogDto logDto = new LogDto();
        logDto.setName("testLog");
        logDto.setFormat("test format");
        when(logService.createLog(any(LogDto.class))).thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        // When & Then
        mockMvc.perform(post("/api/v1/log")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));
    }

    @Test
    void updateLog() throws Exception {
        // Given
        LogDto logDto = new LogDto();
        logDto.setName("testLog");
        logDto.setFormat("test format");
        when(logService.updateLog(any(LogDto.class))).thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        // When & Then
        mockMvc.perform(put("/api/v1/log/testLog")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));
    }

    @Test
    void deleteLog() throws Exception {
        // Given
        when(logService.deleteLog("testLog")).thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        // When & Then
        mockMvc.perform(delete("/api/v1/log/testLog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));
    }

    @Test
    void previewLog() throws Exception {
        // Given
        LogDto logDto = new LogDto();
        logDto.setFormat("test format");
        when(logService.previewLog(any(String.class))).thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        // When & Then
        mockMvc.perform(post("/api/v1/log:preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logDto)))
                .andExpect(status().isOk());
    }

    @Test
    void createLog_returnsError() throws Exception {
        // Given
        LogDto logDto = new LogDto();
        logDto.setName("testLog");
        logDto.setFormat("test format");
        when(logService.createLog(any(LogDto.class))).thenReturn(Result.createResultSet(Result.Type.ERROR, "Duplicate"));

        // When & Then
        mockMvc.perform(post("/api/v1/log")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logDto)))
                .andExpect(jsonPath("$.type").value("ERROR"));
    }

    @Test
    void testCreateLog_missingFormat_returnsError() throws Exception {
        // Given: LogDto with no format (violates @NotEmpty)
        // ValidExceptionHandler returns ERROR/406 (not 400)
        LogDto logDto = new LogDto();
        logDto.setName("testLog");
        // format is null

        // When & Then
        mockMvc.perform(post("/api/v1/log")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logDto)))
                .andExpect(jsonPath("$.type").value("ERROR"));
    }

    @Test
    void testCreateLog_missingName_returnsError() throws Exception {
        // Given: LogDto with no name (violates @NotEmpty)
        // ValidExceptionHandler returns ERROR/406 (not 400)
        LogDto logDto = new LogDto();
        logDto.setFormat("some format");
        // name is null

        // When & Then
        mockMvc.perform(post("/api/v1/log")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logDto)))
                .andExpect(jsonPath("$.type").value("ERROR"));
    }
}

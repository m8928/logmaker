package me.blueat.logmaker.core.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.blueat.logmaker.core.model.SenderDto;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SenderController.class)
class SenderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SenderService senderService;

    @Test
    void getSender() throws Exception {
        // Given
        when(senderService.getSender()).thenReturn(Collections.singletonList(new SenderDto()));

        // When & Then
        mockMvc.perform(get("/api/v1/sender"))
                .andExpect(status().isOk());
    }

    @Test
    void createSender() throws Exception {
        // Given
        SenderDto senderDto = new SenderDto();
        senderDto.setName("testSender");
        senderDto.setType("testType");
        when(senderService.createSender(any(SenderDto.class))).thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        // When & Then
        mockMvc.perform(post("/api/v1/sender")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(senderDto)))
                .andExpect(status().isOk());
    }

    @Test
    void updateSender() throws Exception {
        // Given
        SenderDto senderDto = new SenderDto();
        senderDto.setName("testSender");
        senderDto.setType("testType");
        when(senderService.updateSender(any(SenderDto.class))).thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        // When & Then
        mockMvc.perform(put("/api/v1/sender/testSender")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(senderDto)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteSender() throws Exception {
        // Given
        when(senderService.deleteSender("testSender")).thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        // When & Then
        mockMvc.perform(delete("/api/v1/sender/testSender"))
                .andExpect(status().isOk());
    }
}
package me.blueat.logmaker.core.maker;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.blueat.logmaker.core.model.MakerDto;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MakerController.class)
class MakerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MakerService makerService;

    @Test
    void getMaker() throws Exception {
        // Given
        when(makerService.getMaker()).thenReturn(Collections.singletonList(new MakerDto()));

        // When & Then
        mockMvc.perform(get("/api/v1/maker"))
                .andExpect(status().isOk());
    }

    @Test
    void createMaker() throws Exception {
        // Given
        MakerDto makerDto = new MakerDto();
        makerDto.setName("testMaker");
        makerDto.setType("testType");
        when(makerService.createMaker(any(MakerDto.class))).thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        // When & Then
        mockMvc.perform(post("/api/v1/maker")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(makerDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));
    }

    @Test
    void updateMaker() throws Exception {
        // Given
        MakerDto makerDto = new MakerDto();
        makerDto.setName("testMaker");
        makerDto.setType("testType");
        when(makerService.updateMaker(any(MakerDto.class))).thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        // When & Then
        mockMvc.perform(put("/api/v1/maker/testMaker")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(makerDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));
    }

    @Test
    void deleteMaker() throws Exception {
        // Given
        when(makerService.deleteMaker("testMaker")).thenReturn(Result.createResultSet(Result.Type.SUCCESS, "Success"));

        // When & Then
        mockMvc.perform(delete("/api/v1/maker/testMaker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));
    }

    @Test
    void createMaker_returnsError() throws Exception {
        // Given
        MakerDto makerDto = new MakerDto();
        makerDto.setName("testMaker");
        makerDto.setType("testType");
        when(makerService.createMaker(any(MakerDto.class))).thenReturn(Result.createResultSet(Result.Type.ERROR, "Duplicate"));
    void testCreateMaker_emptyName_returns400() throws Exception {
        // Given
        MakerDto makerDto = new MakerDto();
        makerDto.setName("");
        makerDto.setType("testType");

        // When & Then
        mockMvc.perform(post("/api/v1/maker")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(makerDto)))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.type").value("ERROR"));
    }

    @Test
    void testCreateMaker_missingName_returnsError() throws Exception {
        // Given: MakerDto with no name (violates @NotEmpty)
        // ValidExceptionHandler returns ERROR/406 (not 400)
        MakerDto makerDto = new MakerDto();
        makerDto.setType("testType");
        // name is null - should fail @NotEmpty validation

        // When & Then
        mockMvc.perform(post("/api/v1/maker")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(makerDto)))
                .andExpect(jsonPath("$.type").value("ERROR"));
    }
}
    void testDeleteMaker_nonExistent_returnsError() throws Exception {
        // Given
        when(makerService.deleteMaker("nonExistentMaker")).thenReturn(Result.createResultSet(Result.Type.ERROR, "Maker does not exist"));

        // When & Then
        mockMvc.perform(delete("/api/v1/maker/nonExistentMaker"))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.type").value("ERROR"));
    }

    @Test
    void testUpdateMaker_nonExistent_returnsError() throws Exception {
        // Given
        MakerDto makerDto = new MakerDto();
        makerDto.setName("nonExistentMaker");
        makerDto.setType("testType");
        when(makerService.updateMaker(any(MakerDto.class))).thenReturn(Result.createResultSet(Result.Type.ERROR, "Update maker failed"));

        // When & Then
        mockMvc.perform(put("/api/v1/maker/nonExistentMaker")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(makerDto)))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.type").value("ERROR"));
    }
}

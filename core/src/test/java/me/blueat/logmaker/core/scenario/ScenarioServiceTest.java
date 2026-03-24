package me.blueat.logmaker.core.scenario;

import me.blueat.logmaker.core.config.LogMakerConfig;
import me.blueat.logmaker.core.log.LogService;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.core.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceTest {

    @InjectMocks
    private ScenarioService scenarioService;

    @Mock
    private LogMakerConfig logMakerConfig;

    @Mock
    private MakerService makerService;

    @Mock
    private SenderService senderService;

    @Mock
    private LogService logService;

    private MockedStatic<FileUtil> fileUtilMockedStatic;

    @BeforeEach
    void setUp() {
        when(logMakerConfig.getDataRootPath()).thenReturn(Paths.get("."));
        fileUtilMockedStatic = Mockito.mockStatic(FileUtil.class);
        fileUtilMockedStatic.when(() -> FileUtil.loadFromFile(any(), eq(ScenarioDto[].class)))
                .thenReturn(new ScenarioDto[0]);
        scenarioService.init();
    }

    @AfterEach
    void tearDown() {
        fileUtilMockedStatic.close();
    }

    @Test
    void createScenario() {
        // Given
        ScenarioDto scenarioDto = new ScenarioDto();
        scenarioDto.setName("testScenario");
        scenarioDto.setDescription("A test scenario");

        // When
        ResponseEntity<Result> response = scenarioService.createScenario(scenarioDto);

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }

    @Test
    void createScenario_duplicateName() {
        // Given
        ScenarioDto scenarioDto = new ScenarioDto();
        scenarioDto.setName("testScenario");
        scenarioService.createScenario(scenarioDto);

        // When
        ResponseEntity<Result> response = scenarioService.createScenario(scenarioDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void deleteScenario() {
        // Given
        ScenarioDto scenarioDto = new ScenarioDto();
        scenarioDto.setName("testScenario");
        scenarioService.createScenario(scenarioDto);

        // When
        ResponseEntity<Result> response = scenarioService.deleteScenario("testScenario");

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }
}

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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
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
        scenarioService.destroy();
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

    @Test
    void updateScenario_waitsForRunningThreadBeforeRestart() throws InterruptedException {
        ScenarioDto scenarioDto = scenario("testScenario", 0, 1);
        scenarioService.createScenario(scenarioDto);
        scenarioService.startScenario("testScenario");

        ScenarioThread oldThread = awaitScenarioThread("testScenario");
        assertTrue(oldThread.getRunning().get());

        ResponseEntity<Result> response = scenarioService.updateScenario(
                "testScenario",
                scenario("testScenario", 1, 0)
        );

        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
        assertFalse(oldThread.getRunning().get());

        ScenarioThread restarted = scenarioService.getScenarioThreadMap().get("testScenario");
        if (restarted != null) {
            restarted.interrupt();
        }
    }

    private ScenarioDto scenario(String name, int loopCount, long intervalMs) {
        ScenarioDto scenarioDto = new ScenarioDto();
        scenarioDto.setName(name);
        scenarioDto.setLoopCount(loopCount);
        scenarioDto.setIntervalMinMs(intervalMs);
        scenarioDto.setIntervalMaxMs(intervalMs);
        scenarioDto.setSteps(List.of());
        return scenarioDto;
    }

    private ScenarioThread awaitScenarioThread(String name) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        ScenarioThread thread;
        do {
            thread = scenarioService.getScenarioThreadMap().get(name);
            if (thread != null && thread.getRunning().get()) {
                return thread;
            }
            Thread.yield();
        } while (System.nanoTime() < deadline);
        fail("Scenario thread did not start");
        return null;
    }
}

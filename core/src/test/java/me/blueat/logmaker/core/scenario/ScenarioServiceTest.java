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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
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
    void startScenario_removesThreadWhenSubmitFails() {
        ScenarioDto scenarioDto = scenario("submitFailure", 1, 0);
        scenarioService.createScenario(scenarioDto);
        ExecutorService executor = Mockito.mock(ExecutorService.class);
        when(executor.submit(any(Runnable.class))).thenThrow(new RejectedExecutionException("closed"));
        ReflectionTestUtils.setField(scenarioService, "executorService", executor);

        ResponseEntity<Result> response = scenarioService.startScenario("submitFailure");

        assertEquals(Result.Type.ERROR, response.getBody().getType());
        assertFalse(scenarioService.getScenarioThreadMap().containsKey("submitFailure"));
    }

    @Test
    void updateScenarioStopsQueuedScenarioBeforeReplacingIt() {
        ScenarioDto scenarioDto = scenario("queuedScenario", 0, 1000);
        scenarioService.createScenario(scenarioDto);

        ExecutorService executor = Mockito.mock(ExecutorService.class);
        @SuppressWarnings("unchecked")
        Future<Object> queuedFuture = Mockito.mock(Future.class);
        @SuppressWarnings("unchecked")
        Future<Object> restartedFuture = Mockito.mock(Future.class);
        when(queuedFuture.isDone()).thenReturn(false);
        Mockito.doReturn(queuedFuture, restartedFuture).when(executor).submit(any(Runnable.class));
        ReflectionTestUtils.setField(scenarioService, "executorService", executor);

        ResponseEntity<Result> startResponse = scenarioService.startScenario("queuedScenario");
        ScenarioThread queuedThread = scenarioService.getScenarioThreadMap().get("queuedScenario");

        ResponseEntity<Result> updateResponse = scenarioService.updateScenario(
                "queuedScenario",
                scenario("queuedScenario", 1, 0)
        );

        assertEquals(Result.Type.SUCCESS, startResponse.getBody().getType());
        assertEquals(Result.Type.SUCCESS, updateResponse.getBody().getType());
        Mockito.verify(queuedFuture).cancel(true);
        assertNotSame(queuedThread, scenarioService.getScenarioThreadMap().get("queuedScenario"));
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

    @Test
    void getScenarioReturnsSnapshotWithoutMutatingStoredConfiguration() {
        ScenarioDto scenarioDto = scenario("snapshotScenario", 1, 0);
        scenarioDto.setDescription("original");
        scenarioDto.setSharedVariables(new HashMap<>(Map.of("customer", "customerMaker")));

        ScenarioStepDto step = new ScenarioStepDto();
        step.setLogName("auditLog");
        step.setSenders(new ArrayList<>(List.of("tcpSender")));
        step.setOverrides(new HashMap<>(Map.of("customer", "overrideMaker")));
        scenarioDto.setSteps(new ArrayList<>(List.of(step)));

        scenarioService.createScenario(scenarioDto);
        scenarioDto.setDescription("mutated input");
        scenarioDto.getSharedVariables().put("customer", "mutatedMaker");
        scenarioDto.getSteps().get(0).getSenders().add("mutatedSender");

        ScenarioDto snapshot = scenarioService.getScenario("snapshotScenario");
        snapshot.setDescription("mutated snapshot");
        snapshot.setStatus(true);
        snapshot.getSharedVariables().put("customer", "snapshotMaker");
        snapshot.getSteps().get(0).getSenders().add("snapshotSender");
        snapshot.getSteps().get(0).getOverrides().put("customer", "snapshotOverride");

        ScenarioDto freshSnapshot = scenarioService.getScenario("snapshotScenario");

        assertEquals("original", freshSnapshot.getDescription());
        assertFalse(freshSnapshot.isStatus());
        assertEquals("customerMaker", freshSnapshot.getSharedVariables().get("customer"));
        assertEquals(List.of("tcpSender"), freshSnapshot.getSteps().get(0).getSenders());
        assertEquals("overrideMaker", freshSnapshot.getSteps().get(0).getOverrides().get("customer"));
    }

    @Test
    void startScenarioReportsRuntimeStateWithoutMutatingStoredConfiguration() throws InterruptedException {
        ScenarioDto scenarioDto = scenario("runningSnapshot", 0, 1000);
        scenarioService.createScenario(scenarioDto);

        ResponseEntity<Result> response = scenarioService.startScenario("runningSnapshot");
        ScenarioThread thread = awaitScenarioThread("runningSnapshot");

        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
        assertFalse(scenarioService.getScenarioMap().get("runningSnapshot").isStatus());
        assertTrue(scenarioService.getScenario("runningSnapshot").isStatus());

        thread.interrupt();
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

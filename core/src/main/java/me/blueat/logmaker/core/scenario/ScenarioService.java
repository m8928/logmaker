package me.blueat.logmaker.core.scenario;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.config.LogMakerConfig;
import me.blueat.logmaker.core.log.LogService;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.core.sender.SenderService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static me.blueat.logmaker.core.util.FileUtil.loadFromFile;
import static me.blueat.logmaker.core.util.FileUtil.saveToFile;

@Service
@RequiredArgsConstructor
@Slf4j
@Getter
public class ScenarioService implements DisposableBean {
    private static final String SCENARIO_NOT_FOUND = "Scenario does not exist";
    private static final long STOP_TIMEOUT_MS = 5_000L;
    private static final long STOP_POLL_MS = 25L;

    private final LogMakerConfig logMakerConfig;
    private final MakerService makerService;
    private final SenderService senderService;
    private final LogService logService;

    private ConcurrentHashMap<String, ScenarioDto> scenarioMap;
    private ConcurrentHashMap<String, ScenarioThread> scenarioThreadMap;
    private ExecutorService executorService;

    @PostConstruct
    protected void init() {
        scenarioMap = new ConcurrentHashMap<>();
        scenarioThreadMap = new ConcurrentHashMap<>();
        executorService = Executors.newCachedThreadPool();
        ScenarioDto[] loadedScenarios = loadFromFile(scenarioStoragePath(), ScenarioDto[].class);
        if (loadedScenarios != null) {
            Arrays.stream(loadedScenarios).forEach(dto -> createScenario(dto, true));
        }
        log.info("Initialized Scenario Service");
    }

    @Override
    public void destroy() {
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("ScenarioService ExecutorService did not terminate within 5 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<ScenarioDto> getScenarios() {
        return scenarioMap.values().stream()
                .map(dto -> {
                    ScenarioThread thread = scenarioThreadMap.get(dto.getName());
                    boolean isRunning = thread != null && thread.getRunning().get();
                    if (thread != null && !isRunning) scenarioThreadMap.remove(dto.getName());
                    dto.setStatus(isRunning);
                    dto.setCount(thread != null ? thread.getCount().get() : 0);
                    dto.setCurrentStep(thread != null ? thread.getCurrentStep().get() : 0);
                    dto.setCurrentLoop(thread != null ? thread.getCurrentLoop().get() : 0);
                    dto.setTotalSteps(dto.getSteps() != null ? dto.getSteps().size() : 0);
                    dto.setStepCounts(thread != null ? thread.getStepCounts() : null);
                    return dto;
                })
                .toList();
    }

    public ScenarioDto getScenario(String name) {
        ScenarioDto dto = scenarioMap.get(name);
        if (dto == null) return null;
        ScenarioThread thread = scenarioThreadMap.get(name);
        boolean isRunning = thread != null && thread.getRunning().get();
        if (thread != null && !isRunning) scenarioThreadMap.remove(name);
        dto.setStatus(isRunning);
        dto.setCount(thread != null ? thread.getCount().get() : 0);
        dto.setCurrentStep(thread != null ? thread.getCurrentStep().get() : 0);
        dto.setCurrentLoop(thread != null ? thread.getCurrentLoop().get() : 0);
        dto.setTotalSteps(dto.getSteps() != null ? dto.getSteps().size() : 0);
        dto.setStepCounts(thread != null ? thread.getStepCounts() : null);
        return dto;
    }

    public ResponseEntity<Result> createScenario(ScenarioDto scenarioDto) {
        return createScenario(scenarioDto, false);
    }

    public ResponseEntity<Result> createScenario(ScenarioDto scenarioDto, boolean isImport) {
        if (scenarioDto == null || scenarioDto.getName() == null || scenarioDto.getName().isBlank()) {
            return Result.createResultSet(Result.Type.ERROR, "Scenario name is required");
        }

        if (scenarioMap.putIfAbsent(scenarioDto.getName(), scenarioDto) != null) {
            return Result.createResultSet(Result.Type.ERROR,
                    String.format("%s is the scenario name already in use", scenarioDto.getName()));
        }

        if (!isImport) {
            saveScenarios();
        }

        return Result.createResultSet(Result.Type.SUCCESS, "Successful scenario registration");
    }

    public ResponseEntity<Result> updateScenario(String name, ScenarioDto scenarioDto) {
        if (!scenarioMap.containsKey(name)) {
            return Result.createResultSet(Result.Type.ERROR, SCENARIO_NOT_FOUND);
        }
        if (scenarioDto == null) {
            return Result.createResultSet(Result.Type.ERROR, "Scenario payload is required");
        }

        boolean wasRunning = false;
        ScenarioThread existing = scenarioThreadMap.remove(name);
        if (existing != null && existing.getRunning().get()) {
            wasRunning = true;
            if (!stopScenarioThread(name, existing)) {
                scenarioThreadMap.put(name, existing);
                return Result.createResultSet(Result.Type.ERROR,
                        "Scenario did not stop before update");
            }
        }

        scenarioDto.setName(name);
        scenarioMap.put(name, scenarioDto);
        saveScenarios();

        if (wasRunning) {
            ResponseEntity<Result> startResult = startScenario(name);
            Result startBody = startResult.getBody();
            if (startBody == null || !Result.Type.SUCCESS.equals(startBody.getType())) {
                return startResult;
            }
        }

        return Result.createResultSet(Result.Type.SUCCESS, "Successfully updated scenario");
    }

    public ResponseEntity<Result> deleteScenario(String name) {
        ScenarioDto removed = scenarioMap.remove(name);
        if (removed == null) {
            return Result.createResultSet(Result.Type.ERROR, SCENARIO_NOT_FOUND);
        }

        ScenarioThread thread = scenarioThreadMap.remove(name);
        if (thread != null) {
            stopScenarioThread(name, thread);
        }

        saveScenarios();
        return Result.createResultSet(Result.Type.SUCCESS, "Successfully deleted scenario");
    }

    public ResponseEntity<Result> startScenario(String name) {
        ScenarioDto scenarioDto = scenarioMap.get(name);
        if (scenarioDto == null) {
            return Result.createResultSet(Result.Type.ERROR, SCENARIO_NOT_FOUND);
        }

        while (true) {
            ScenarioThread existing = scenarioThreadMap.get(name);
            if (existing != null && existing.getRunning().get()) {
                return Result.createResultSet(Result.Type.ERROR, "Scenario is already running");
            }
            if (existing != null && !scenarioThreadMap.remove(name, existing)) {
                continue;
            }

            ScenarioThread thread = new ScenarioThread(makerService, senderService, logService, scenarioDto);
            if (scenarioThreadMap.putIfAbsent(name, thread) != null) {
                continue;
            }

            try {
                executorService.submit(thread);
            } catch (RuntimeException e) {
                scenarioThreadMap.remove(name, thread);
                thread.interrupt();
                log.error("Failed to start scenario thread: {}", name, e);
                return Result.createResultSet(Result.Type.ERROR, "Scenario thread start failed");
            }
            scenarioDto.setStatus(true);

            return Result.createResultSet(Result.Type.SUCCESS, "Scenario started");
        }
    }

    public ResponseEntity<Result> stopScenario(String name) {
        ScenarioThread thread = scenarioThreadMap.remove(name);
        if (thread == null) {
            return Result.createResultSet(Result.Type.ERROR, "Scenario is not running");
        }

        stopScenarioThread(name, thread);
        ScenarioDto dto = scenarioMap.get(name);
        if (dto != null) {
            dto.setStatus(false);
        }

        return Result.createResultSet(Result.Type.SUCCESS, "Scenario stopped");
    }

    private boolean stopScenarioThread(String name, ScenarioThread thread) {
        thread.interrupt();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(STOP_TIMEOUT_MS);
        while (thread.getRunning().get() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(STOP_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        boolean stopped = !thread.getRunning().get();
        if (!stopped) {
            log.warn("Scenario {} did not stop within {} ms", name, STOP_TIMEOUT_MS);
        }
        return stopped;
    }

    private void saveScenarios() {
        List<ScenarioDto> toSave = scenarioMap.values().stream()
                .map(dto -> {
                    // Save without runtime fields
                    ScenarioDto clean = new ScenarioDto();
                    clean.setName(dto.getName());
                    clean.setDescription(dto.getDescription());
                    clean.setSharedVariables(dto.getSharedVariables());
                    clean.setSteps(dto.getSteps());
                    clean.setIntervalMinMs(dto.getIntervalMinMs());
                    clean.setIntervalMaxMs(dto.getIntervalMaxMs());
                    clean.setLoopCount(dto.getLoopCount());
                    return clean;
                })
                .toList();
        saveToFile(toSave, scenarioStoragePath());
    }

    private String scenarioStoragePath() {
        return String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "scenarios.json");
    }
}

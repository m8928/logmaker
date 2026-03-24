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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static me.blueat.logmaker.core.util.FileUtil.loadFromFile;
import static me.blueat.logmaker.core.util.FileUtil.saveToFile;

@Service
@RequiredArgsConstructor
@Slf4j
@Getter
public class ScenarioService {

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
        executorService = Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors())
        );
        Arrays.stream(Objects.requireNonNull(loadFromFile(
                String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "scenarios.json"),
                ScenarioDto[].class)))
                .forEach(dto -> createScenario(dto, true));
        log.info("Initialized Scenario Service");
    }

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
                    dto.setStatus(thread != null);
                    dto.setCount(thread != null ? thread.getCount().get() : 0);
                    dto.setCurrentEps(thread != null ? thread.getCurrentEps() : 0);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public ScenarioDto getScenario(String name) {
        ScenarioDto dto = scenarioMap.get(name);
        if (dto == null) return null;
        ScenarioThread thread = scenarioThreadMap.get(name);
        dto.setStatus(thread != null);
        dto.setCount(thread != null ? thread.getCount().get() : 0);
        dto.setCurrentEps(thread != null ? thread.getCurrentEps() : 0);
        return dto;
    }

    public ResponseEntity<Result> createScenario(ScenarioDto scenarioDto) {
        return createScenario(scenarioDto, false);
    }

    public ResponseEntity<Result> createScenario(ScenarioDto scenarioDto, boolean isImport) {
        if (scenarioMap.containsKey(scenarioDto.getName())) {
            return Result.createResultSet(Result.Type.ERROR,
                    String.format("%s is the scenario name already in use", scenarioDto.getName()));
        }

        scenarioMap.put(scenarioDto.getName(), scenarioDto);

        if (!isImport) {
            saveScenarios();
        }

        return Result.createResultSet(Result.Type.SUCCESS, "Successful scenario registration");
    }

    public ResponseEntity<Result> updateScenario(String name, ScenarioDto scenarioDto) {
        if (!scenarioMap.containsKey(name)) {
            return Result.createResultSet(Result.Type.ERROR, "Scenario does not exist");
        }

        scenarioDto.setName(name);
        scenarioMap.put(name, scenarioDto);
        saveScenarios();
        return Result.createResultSet(Result.Type.SUCCESS, "Successfully updated scenario");
    }

    public ResponseEntity<Result> deleteScenario(String name) {
        ScenarioDto removed = scenarioMap.remove(name);
        if (removed == null) {
            return Result.createResultSet(Result.Type.ERROR, "Scenario does not exist");
        }

        ScenarioThread thread = scenarioThreadMap.remove(name);
        if (thread != null) {
            thread.interrupt();
        }

        saveScenarios();
        return Result.createResultSet(Result.Type.SUCCESS, "Successfully deleted scenario");
    }

    public ResponseEntity<Result> startScenario(String name) {
        ScenarioDto scenarioDto = scenarioMap.get(name);
        if (scenarioDto == null) {
            return Result.createResultSet(Result.Type.ERROR, "Scenario does not exist");
        }

        if (scenarioThreadMap.containsKey(name)) {
            return Result.createResultSet(Result.Type.ERROR, "Scenario is already running");
        }

        ScenarioThread thread = new ScenarioThread(makerService, senderService, logService, scenarioDto);
        scenarioThreadMap.put(name, thread);
        executorService.submit(thread);
        scenarioDto.setStatus(true);

        return Result.createResultSet(Result.Type.SUCCESS, "Scenario started");
    }

    public ResponseEntity<Result> stopScenario(String name) {
        ScenarioThread thread = scenarioThreadMap.remove(name);
        if (thread == null) {
            return Result.createResultSet(Result.Type.ERROR, "Scenario is not running");
        }

        thread.interrupt();
        ScenarioDto dto = scenarioMap.get(name);
        if (dto != null) {
            dto.setStatus(false);
        }

        return Result.createResultSet(Result.Type.SUCCESS, "Scenario stopped");
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
                    clean.setSenders(dto.getSenders());
                    clean.setEps(dto.getEps());
                    clean.setLoopCount(dto.getLoopCount());
                    return clean;
                })
                .collect(Collectors.toList());
        saveToFile(toSave, String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "scenarios.json"));
    }
}

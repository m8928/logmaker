package me.blueat.logmaker.core.scenario;

import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static me.blueat.logmaker.core.util.FileUtil.loadFromFile;
import static me.blueat.logmaker.core.util.FileUtil.saveToFile;

@Service
@RequiredArgsConstructor
@Slf4j
@Getter
public class ScenarioService implements DisposableBean {
    private static final String SCENARIO_NOT_FOUND = "Scenario does not exist";
    private static final String SCENARIO_BUSY = "Scenario is busy";
    private static final long STOP_TIMEOUT_MS = 5_000L;
    private static final long STOP_POLL_MS = 25L;

    private final LogMakerConfig logMakerConfig;
    private final MakerService makerService;
    private final SenderService senderService;
    private final LogService logService;

    @Getter(AccessLevel.NONE)
    private final Object scenarioStateLock = new Object();
    private ConcurrentHashMap<String, ScenarioDto> scenarioMap;
    private ConcurrentHashMap<String, ScenarioThread> scenarioThreadMap;
    @Getter(AccessLevel.NONE)
    private Set<String> scenarioTransitions;
    private ExecutorService executorService;

    private record TransitionResult(ScenarioThread thread, boolean active, ResponseEntity<Result> error) {
        private boolean failed() {
            return error != null;
        }
    }

    private record StartLookup(ScenarioDto scenarioDto, ResponseEntity<Result> error) {
        private boolean failed() {
            return error != null;
        }
    }

    private record StartAttempt(boolean retry, ResponseEntity<Result> response) {
    }

    @PostConstruct
    protected void init() {
        scenarioMap = new ConcurrentHashMap<>();
        scenarioThreadMap = new ConcurrentHashMap<>();
        scenarioTransitions = new HashSet<>();
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
                .map(this::scenarioSnapshot)
                .toList();
    }

    public ScenarioDto getScenario(String name) {
        ScenarioDto dto = scenarioMap.get(name);
        if (dto == null) return null;
        return scenarioSnapshot(dto);
    }

    public ResponseEntity<Result> createScenario(ScenarioDto scenarioDto) {
        return createScenario(scenarioDto, false);
    }

    public ResponseEntity<Result> createScenario(ScenarioDto scenarioDto, boolean isImport) {
        if (scenarioDto == null || scenarioDto.getName() == null || scenarioDto.getName().isBlank()) {
            return Result.createResultSet(Result.Type.ERROR, "Scenario name is required");
        }

        ScenarioDto storedScenario = copyScenarioConfig(scenarioDto);
        synchronized (scenarioStateLock) {
            if (scenarioMap.containsKey(storedScenario.getName())) {
                return Result.createResultSet(Result.Type.ERROR,
                        String.format("%s is the scenario name already in use", storedScenario.getName()));
            }
        }

        Optional<String> validationError = validateScenarioReferences(storedScenario);
        if (validationError.isPresent()) {
            return Result.createResultSet(Result.Type.ERROR, validationError.get());
        }

        synchronized (scenarioStateLock) {
            if (scenarioMap.putIfAbsent(storedScenario.getName(), storedScenario) != null) {
                return Result.createResultSet(Result.Type.ERROR,
                        String.format("%s is the scenario name already in use", storedScenario.getName()));
            }
        }

        if (!isImport) {
            saveScenarios();
        }

        return Result.createResultSet(Result.Type.SUCCESS, "Successful scenario registration");
    }

    public ResponseEntity<Result> updateScenario(String name, ScenarioDto scenarioDto) {
        ResponseEntity<Result> requestError = validateUpdateRequest(name, scenarioDto);
        if (requestError != null) {
            return requestError;
        }

        ScenarioDto storedScenario = copyScenarioConfig(scenarioDto);
        storedScenario.setName(name);
        Optional<String> validationError = validateScenarioReferences(storedScenario);
        if (validationError.isPresent()) {
            return Result.createResultSet(Result.Type.ERROR, validationError.get());
        }

        TransitionResult transition = removeScenarioThreadForTransition(name);
        if (transition.failed()) {
            return transition.error();
        }

        try {
            ResponseEntity<Result> stopError = stopForUpdate(name, transition);
            if (stopError != null) {
                return stopError;
            }

            ResponseEntity<Result> replaceError = replaceScenarioConfig(name, storedScenario, transition);
            if (replaceError != null) {
                return replaceError;
            }

            saveScenarios();
        } finally {
            exitScenarioTransition(name);
        }

        ResponseEntity<Result> restartError = restartScenarioIfNeeded(name, transition.active());
        if (restartError != null) {
            return restartError;
        }

        return Result.createResultSet(Result.Type.SUCCESS, "Successfully updated scenario");
    }

    public ResponseEntity<Result> deleteScenario(String name) {
        ScenarioDto removed;
        ScenarioThread thread;
        synchronized (scenarioStateLock) {
            if (!scenarioTransitions.add(name)) {
                return Result.createResultSet(Result.Type.ERROR, SCENARIO_BUSY);
            }

            removed = scenarioMap.remove(name);
            if (removed == null) {
                scenarioTransitions.remove(name);
                return Result.createResultSet(Result.Type.ERROR, SCENARIO_NOT_FOUND);
            }
            thread = scenarioThreadMap.remove(name);
        }

        try {
            if (thread != null && !stopScenarioThread(name, thread)) {
                synchronized (scenarioStateLock) {
                    scenarioMap.putIfAbsent(name, removed);
                    scenarioThreadMap.putIfAbsent(name, thread);
                }
                return Result.createResultSet(Result.Type.ERROR, "Scenario did not stop before deletion");
            }
            saveScenarios();
        } finally {
            synchronized (scenarioStateLock) {
                scenarioTransitions.remove(name);
            }
        }

        return Result.createResultSet(Result.Type.SUCCESS, "Successfully deleted scenario");
    }

    public ResponseEntity<Result> startScenario(String name) {
        while (true) {
            StartLookup lookup = findScenarioForStart(name);
            if (lookup.failed()) {
                return lookup.error();
            }

            Optional<String> validationError = validateScenarioReferences(lookup.scenarioDto());
            if (validationError.isPresent()) {
                return Result.createResultSet(Result.Type.ERROR, validationError.get());
            }

            StartAttempt attempt = submitScenarioIfUnchanged(name, lookup.scenarioDto());
            if (!attempt.retry()) {
                return attempt.response();
            }
        }
    }

    public ResponseEntity<Result> stopScenario(String name) {
        ScenarioThread thread;
        synchronized (scenarioStateLock) {
            if (scenarioTransitions.contains(name)) {
                return Result.createResultSet(Result.Type.ERROR, SCENARIO_BUSY);
            }

            thread = scenarioThreadMap.remove(name);
            if (thread == null) {
                return Result.createResultSet(Result.Type.ERROR, "Scenario is not running");
            }
            scenarioTransitions.add(name);
        }

        try {
            if (!stopScenarioThread(name, thread)) {
                synchronized (scenarioStateLock) {
                    scenarioThreadMap.putIfAbsent(name, thread);
                }
                return Result.createResultSet(Result.Type.ERROR, "Scenario did not stop");
            }
        } finally {
            synchronized (scenarioStateLock) {
                scenarioTransitions.remove(name);
            }
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

    private boolean isScenarioThreadActive(ScenarioThread thread) {
        Future<?> task = thread.getRunningTask();
        return thread.getRunning().get() || (task != null && !task.isDone());
    }

    private ResponseEntity<Result> validateUpdateRequest(String name, ScenarioDto scenarioDto) {
        synchronized (scenarioStateLock) {
            if (!scenarioMap.containsKey(name)) {
                return Result.createResultSet(Result.Type.ERROR, SCENARIO_NOT_FOUND);
            }
        }
        if (scenarioDto == null) {
            return Result.createResultSet(Result.Type.ERROR, "Scenario payload is required");
        }
        return null;
    }

    private TransitionResult removeScenarioThreadForTransition(String name) {
        synchronized (scenarioStateLock) {
            if (!scenarioMap.containsKey(name)) {
                return new TransitionResult(null, false,
                        Result.createResultSet(Result.Type.ERROR, SCENARIO_NOT_FOUND));
            }
            if (!scenarioTransitions.add(name)) {
                return new TransitionResult(null, false,
                        Result.createResultSet(Result.Type.ERROR, SCENARIO_BUSY));
            }
            ScenarioThread thread = scenarioThreadMap.remove(name);
            return new TransitionResult(thread, thread != null && isScenarioThreadActive(thread), null);
        }
    }

    private ResponseEntity<Result> stopForUpdate(String name, TransitionResult transition) {
        if (!transition.active() || stopScenarioThread(name, transition.thread())) {
            return null;
        }

        synchronized (scenarioStateLock) {
            scenarioThreadMap.putIfAbsent(name, transition.thread());
        }
        return Result.createResultSet(Result.Type.ERROR, "Scenario did not stop before update");
    }

    private ResponseEntity<Result> replaceScenarioConfig(String name, ScenarioDto storedScenario,
                                                         TransitionResult transition) {
        synchronized (scenarioStateLock) {
            if (scenarioMap.containsKey(name)) {
                scenarioMap.put(name, storedScenario);
                return null;
            }
            restoreScenarioThread(name, transition);
            return Result.createResultSet(Result.Type.ERROR, SCENARIO_NOT_FOUND);
        }
    }

    private void restoreScenarioThread(String name, TransitionResult transition) {
        if (transition.active() && transition.thread() != null) {
            scenarioThreadMap.putIfAbsent(name, transition.thread());
        }
    }

    private void exitScenarioTransition(String name) {
        synchronized (scenarioStateLock) {
            scenarioTransitions.remove(name);
        }
    }

    private ResponseEntity<Result> restartScenarioIfNeeded(String name, boolean wasRunning) {
        if (!wasRunning) {
            return null;
        }

        ResponseEntity<Result> startResult = startScenario(name);
        Result startBody = startResult.getBody();
        if (startBody == null || !Result.Type.SUCCESS.equals(startBody.getType())) {
            return startResult;
        }
        return null;
    }

    private StartLookup findScenarioForStart(String name) {
        synchronized (scenarioStateLock) {
            if (scenarioTransitions.contains(name)) {
                return new StartLookup(null, Result.createResultSet(Result.Type.ERROR, SCENARIO_BUSY));
            }
            ScenarioDto scenarioDto = scenarioMap.get(name);
            if (scenarioDto == null) {
                return new StartLookup(null, Result.createResultSet(Result.Type.ERROR, SCENARIO_NOT_FOUND));
            }
            return new StartLookup(scenarioDto, null);
        }
    }

    private StartAttempt submitScenarioIfUnchanged(String name, ScenarioDto scenarioDto) {
        synchronized (scenarioStateLock) {
            if (scenarioTransitions.contains(name)) {
                return new StartAttempt(false, Result.createResultSet(Result.Type.ERROR, SCENARIO_BUSY));
            }
            if (scenarioMap.get(name) != scenarioDto) {
                return new StartAttempt(true, null);
            }

            ScenarioThread existing = scenarioThreadMap.get(name);
            if (existing != null && isScenarioThreadActive(existing)) {
                return new StartAttempt(false, Result.createResultSet(Result.Type.ERROR, "Scenario is already running"));
            }
            if (existing != null) {
                scenarioThreadMap.remove(name);
            }

            ScenarioThread thread = new ScenarioThread(makerService, senderService, logService, scenarioDto);
            scenarioThreadMap.put(name, thread);
            return new StartAttempt(false, submitScenarioThread(name, thread));
        }
    }

    private ResponseEntity<Result> submitScenarioThread(String name, ScenarioThread thread) {
        try {
            Future<?> task = executorService.submit(thread);
            thread.attachRunningTask(task);
            return Result.createResultSet(Result.Type.SUCCESS, "Scenario started");
        } catch (RuntimeException e) {
            scenarioThreadMap.remove(name, thread);
            thread.interrupt();
            log.error("Failed to start scenario thread: {}", name, e);
            return Result.createResultSet(Result.Type.ERROR, "Scenario thread start failed");
        }
    }

    private Optional<String> validateScenarioReferences(ScenarioDto scenarioDto) {
        List<ScenarioStepDto> steps = scenarioDto.getSteps() != null ? scenarioDto.getSteps() : List.of();
        for (int index = 0; index < steps.size(); index++) {
            ScenarioStepDto step = steps.get(index);
            if (step == null) {
                return Optional.of(String.format("Scenario step %d is required", index + 1));
            }
            if (!hasText(step.getLogName())) {
                return Optional.of(String.format("Scenario step %d log is required", index + 1));
            }
            if (!logExists(step.getLogName())) {
                return Optional.of(String.format("Scenario references unknown log: %s", step.getLogName()));
            }

            Optional<String> missingSender = missingSenderName(step.getSenders());
            if (missingSender.isPresent()) {
                return missingSender;
            }
        }

        return missingSharedVariableMaker(scenarioDto.getSharedVariables());
    }

    private Optional<String> missingSenderName(List<String> senderNames) {
        if (senderNames == null) {
            return Optional.empty();
        }

        Set<String> checked = new HashSet<>();
        for (String senderName : senderNames) {
            if (!hasText(senderName)) {
                return Optional.of("Scenario step sender is required");
            }
            if (checked.add(senderName) && !senderExists(senderName)) {
                return Optional.of(String.format("Scenario references unknown sender: %s", senderName));
            }
        }

        return Optional.empty();
    }

    private Optional<String> missingSharedVariableMaker(Map<String, String> sharedVariables) {
        if (sharedVariables == null || sharedVariables.isEmpty()) {
            return Optional.empty();
        }

        Set<String> checked = new HashSet<>();
        for (Map.Entry<String, String> entry : sharedVariables.entrySet()) {
            String makerName = entry.getValue();
            if (!hasText(makerName)) {
                return Optional.of(String.format("Scenario shared variable %s maker is required", entry.getKey()));
            }
            if (checked.add(makerName) && !makerExists(makerName)) {
                return Optional.of(String.format("Scenario references unknown maker: %s", makerName));
            }
        }

        return Optional.empty();
    }

    private boolean logExists(String logName) {
        try {
            return logService.getLog(logName) != null;
        } catch (RuntimeException e) {
            log.warn("Failed to validate scenario log reference: {}", logName, e);
            return false;
        }
    }

    private boolean senderExists(String senderName) {
        try {
            return senderService.getSender(senderName).isPresent();
        } catch (RuntimeException e) {
            log.warn("Failed to validate scenario sender reference: {}", senderName, e);
            return false;
        }
    }

    private boolean makerExists(String makerName) {
        try {
            return makerService.getMaker(makerName).isPresent();
        } catch (RuntimeException e) {
            log.warn("Failed to validate scenario maker reference: {}", makerName, e);
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void saveScenarios() {
        List<ScenarioDto> toSave = scenarioMap.values().stream()
                .map(this::copyScenarioConfig)
                .toList();
        saveToFile(toSave, scenarioStoragePath());
    }

    private ScenarioDto scenarioSnapshot(ScenarioDto dto) {
        ScenarioThread thread = activeThread(dto.getName());
        ScenarioDto snapshot = copyScenarioConfig(dto);
        snapshot.setStatus(thread != null);
        snapshot.setCount(thread != null ? thread.getCount().get() : 0);
        snapshot.setCurrentStep(thread != null ? thread.getCurrentStep().get() : 0);
        snapshot.setCurrentLoop(thread != null ? thread.getCurrentLoop().get() : 0);
        snapshot.setTotalSteps(snapshot.getSteps() != null ? snapshot.getSteps().size() : 0);
        snapshot.setStepCounts(thread != null ? copyStepCounts(thread.getStepCounts()) : null);
        return snapshot;
    }

    private ScenarioThread activeThread(String name) {
        ScenarioThread thread = scenarioThreadMap.get(name);
        if (thread != null && !isScenarioThreadActive(thread)) {
            scenarioThreadMap.remove(name, thread);
            return null;
        }
        return thread;
    }

    private ScenarioDto copyScenarioConfig(ScenarioDto dto) {
        ScenarioDto copy = new ScenarioDto();
        copy.setName(dto.getName());
        copy.setDescription(dto.getDescription());
        copy.setSharedVariables(copyMap(dto.getSharedVariables()));
        copy.setSteps(copySteps(dto.getSteps()));
        copy.setSenders(copyList(dto.getSenders()));
        copy.setIntervalMinMs(dto.getIntervalMinMs());
        copy.setIntervalMaxMs(dto.getIntervalMaxMs());
        copy.setLoopCount(dto.getLoopCount());
        return copy;
    }

    private List<ScenarioStepDto> copySteps(List<ScenarioStepDto> steps) {
        List<ScenarioStepDto> copies = new ArrayList<>();
        if (steps == null) {
            return copies;
        }
        for (ScenarioStepDto step : steps) {
            copies.add(copyStep(step));
        }
        return copies;
    }

    private ScenarioStepDto copyStep(ScenarioStepDto step) {
        if (step == null) {
            return null;
        }
        ScenarioStepDto copy = new ScenarioStepDto();
        copy.setLogName(step.getLogName());
        copy.setRepeat(step.getRepeat());
        copy.setDelayMinMs(step.getDelayMinMs());
        copy.setDelayMaxMs(step.getDelayMaxMs());
        copy.setSenders(copyList(step.getSenders()));
        copy.setOverrides(copyMap(step.getOverrides()));
        return copy;
    }

    private List<String> copyList(List<String> values) {
        return values != null ? new ArrayList<>(values) : new ArrayList<>();
    }

    private Map<String, String> copyMap(Map<String, String> values) {
        return values != null ? new HashMap<>(values) : new HashMap<>();
    }

    private long[] copyStepCounts(long[] stepCounts) {
        return stepCounts != null ? Arrays.copyOf(stepCounts, stepCounts.length) : null;
    }

    private String scenarioStoragePath() {
        return String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "scenarios.json");
    }
}

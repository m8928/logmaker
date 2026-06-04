package me.blueat.logmaker.core.scenario;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.log.LogService;
import me.blueat.logmaker.core.log.LogThread;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.core.util.TextSizeUtil;
import me.blueat.logmaker.core.util.VelocityTemplateUtil;
import me.blueat.logmaker.plugin.api.sender.Sender;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Getter
public class ScenarioThread implements Runnable {
    private final MakerService makerService;
    private final SenderService senderService;
    private final LogService logService;
    private final ScenarioDto scenarioDto;
    private final VelocityEngine overrideEngine = VelocityTemplateUtil.createSecureEngine(4);
    private final Map<String, Template> overrideTemplateCache = new ConcurrentHashMap<>();
    private final AtomicLong overrideTemplateId = new AtomicLong();

    private final AtomicLong count = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger currentStep = new AtomicInteger(-1);
    private final AtomicInteger currentLoop = new AtomicInteger(0);
    private volatile long[] stepCounts;
    private volatile Future<?> runningTask;
    private volatile boolean interrupted;

    public ScenarioThread(MakerService makerService, SenderService senderService,
                          LogService logService, ScenarioDto scenarioDto) {
        this.makerService = makerService;
        this.senderService = senderService;
        this.logService = logService;
        this.scenarioDto = scenarioDto;
    }

    @Override
    public void run() {
        running.set(true);
        Map<String, Sender<?>> senders = new ConcurrentHashMap<>();
        try {
            if (interrupted) {
                Thread.currentThread().interrupt();
                return;
            }

            List<ScenarioStepDto> steps = scenarioDto.getSteps() != null ? scenarioDto.getSteps() : Collections.emptyList();
            stepCounts = new long[steps.size()];

            resolveSenders(steps, senders);
            executeLoops(steps, senders);
        } catch (RuntimeException | Error e) {
            log.error("Scenario thread failed: {}", scenarioDto.getName(), e);
            throw e;
        } finally {
            senders.values().forEach(Sender::decreaseRef);
            running.set(false);
            runningTask = null;
        }
    }

    private void executeLoops(List<ScenarioStepDto> steps, Map<String, Sender<?>> senders) {
        int loopCount = scenarioDto.getLoopCount();
        long intervalMinMs = scenarioDto.getIntervalMinMs();
        long intervalMaxMs = scenarioDto.getIntervalMaxMs();
        boolean infinite = (loopCount == 0);
        int loop = 0;

        while (shouldRunLoop(infinite, loop, loopCount)) {
            currentLoop.set(loop + 1);
            executeSteps(steps, senders, resolveSharedVariables());
            loop++;
            sleepBetweenLoops(infinite, loop, loopCount, intervalMinMs, intervalMaxMs);
        }
    }

    private boolean shouldRunLoop(boolean infinite, int loop, int loopCount) {
        return !Thread.currentThread().isInterrupted() && !interrupted && (infinite || loop < loopCount);
    }

    private void sleepBetweenLoops(boolean infinite, int loop, int loopCount,
                                   long intervalMinMs, long intervalMaxMs) {
        if (shouldRunLoop(infinite, loop, loopCount)) {
            sleepMillis(randomLong(intervalMinMs, intervalMaxMs));
        }
    }

    private void executeSteps(List<ScenarioStepDto> steps, Map<String, Sender<?>> senders,
                              Map<String, String> resolvedVars) {
        for (int stepIdx = 0; stepIdx < steps.size()
                && !Thread.currentThread().isInterrupted()
                && !interrupted; stepIdx++) {
            executeStep(steps.get(stepIdx), stepIdx, senders, resolvedVars);
        }
    }

    private void executeStep(ScenarioStepDto step, int stepIdx, Map<String, Sender<?>> senders,
                             Map<String, String> resolvedVars) {
        currentStep.set(stepIdx + 1);
        Optional<LogThread> logThread = resolveStepLog(step);
        logThread.ifPresent(thread -> executeRepeats(step, stepIdx, senders, resolvedVars, thread));
    }

    private Optional<LogThread> resolveStepLog(ScenarioStepDto step) {
        LogThread logThread = logService.getLog(step.getLogName());
        if (logThread == null) {
            log.warn("Scenario step references unknown log: {}", step.getLogName());
            return Optional.empty();
        }

        return Optional.of(logThread);
    }

    private void executeRepeats(ScenarioStepDto step, int stepIdx, Map<String, Sender<?>> senders,
                                Map<String, String> resolvedVars, LogThread logThread) {
        for (int repeat = 0; repeat < step.getRepeat()
                && !Thread.currentThread().isInterrupted()
                && !interrupted; repeat++) {
            if (!sleepMillis(randomLong(step.getDelayMinMs(), step.getDelayMaxMs()))) {
                return;
            }
            sendStepData(step, stepIdx, senders, resolvedVars, logThread);
        }
    }

    private void sendStepData(ScenarioStepDto step, int stepIdx, Map<String, Sender<?>> senders,
                              Map<String, String> resolvedVars, LogThread logThread) {
        Map<String, Object> templateData = buildTemplateData(logThread, step, resolvedVars);
        String data = generate(logThread.getVTemplate(), templateData);
        int dataBytes = TextSizeUtil.utf8Length(data);

        getSendersForStep(senders, step).forEach(sender -> sendToSender(sender, data, dataBytes));
        count.incrementAndGet();
        incrementStepCount(stepIdx);
    }

    private void incrementStepCount(int stepIdx) {
        if (stepIdx < stepCounts.length) {
            stepCounts[stepIdx]++;
        }
    }

    private boolean sleepMillis(long sleepMs) {
        if (interrupted || Thread.currentThread().isInterrupted()) {
            return false;
        }
        if (sleepMs <= 0) {
            return true;
        }

        try {
            Thread.sleep(sleepMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void resolveSenders(List<ScenarioStepDto> steps, Map<String, Sender<?>> senders) {
        Set<String> senderNames = new LinkedHashSet<>();

        steps.forEach(step -> addSenderNames(senderNames, step.getSenders()));

        senderNames.forEach(senderName -> {
            Map.Entry<String, Sender<?>> entry = senderService.getSender(senderName)
                    .orElseThrow(() -> new IllegalStateException("sender not found. " + senderName));
            entry.getValue().increaseRef();
            senders.put(senderName, entry.getValue());
        });
    }

    private List<Sender<?>> getSendersForStep(Map<String, Sender<?>> senders, ScenarioStepDto step) {
        List<String> senderNames = step.getSenders();
        if (senderNames == null || senderNames.isEmpty()) {
            return Collections.emptyList();
        }

        return senderNames.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(senders::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private void addSenderNames(Set<String> target, List<String> source) {
        if (source == null) {
            return;
        }
        source.stream()
                .filter(Objects::nonNull)
                .forEach(target::add);
    }

    @SuppressWarnings("java:S2245")
    private long randomLong(long min, long max) {
        if (min >= max) return min;
        // Simulation delays are not security-sensitive randomness.
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private Map<String, String> resolveSharedVariables() {
        Map<String, String> resolved = new HashMap<>();
        sharedVariables().forEach((varName, makerName) ->
                makerService.getMaker(makerName).ifPresent(entry -> {
                    Object data = entry.getValue().getData();
                    resolved.put(varName, data != null ? data.toString() : "");
                })
        );
        return resolved;
    }

    private Map<String, String> sharedVariables() {
        Map<String, String> variables = scenarioDto.getSharedVariables();
        return variables != null ? variables : Collections.emptyMap();
    }

    private Map<String, String> stepOverrides(ScenarioStepDto step) {
        Map<String, String> overrides = step.getOverrides();
        return overrides != null ? overrides : Collections.emptyMap();
    }

    private Map<String, Object> buildTemplateData(LogThread logThread, ScenarioStepDto step, Map<String, String> resolvedVars) {
        Map<String, Object> data = new HashMap<>();

        Set<String> suppliedKeys = new HashSet<>(stepOverrides(step).keySet());
        suppliedKeys.addAll(resolvedVars.keySet());

        logThread.getMakerName().forEach(makerName -> {
            if (!suppliedKeys.contains(makerName)) {
                makerService.getMaker(makerName).ifPresent(entry ->
                        data.put(makerName, entry.getValue().getData())
                );
            }
        });

        data.putAll(resolveStepOverrides(step, resolvedVars));
        data.putAll(resolvedVars);

        return data;
    }

    private Map<String, String> resolveStepOverrides(ScenarioStepDto step, Map<String, String> resolvedVars) {
        Map<String, String> resolved = new HashMap<>();
        stepOverrides(step).forEach((key, value) -> resolved.put(key, resolveOverrideValue(value, resolvedVars)));
        return resolved;
    }

    private String resolveOverrideValue(String value, Map<String, String> resolvedVars) {
        if (value == null || resolvedVars.isEmpty()) {
            return value;
        }
        if (!value.contains("$") && !value.contains("#")) {
            return value;
        }

        VelocityContext context = new VelocityContext();
        resolvedVars.forEach(context::put);
        StringWriter writer = new StringWriter();
        try {
            Template template = overrideTemplateCache.computeIfAbsent(value, this::compileOverrideTemplate);
            template.merge(context, writer);
            return writer.toString();
        } catch (Exception e) {
            log.error("Failed to evaluate override template: {}", value, e);
            return value;
        }
    }

    private Template compileOverrideTemplate(String value) {
        return VelocityTemplateUtil.compile(
                overrideEngine,
                "scenario-step-override-" + overrideTemplateId.incrementAndGet(),
                value
        );
    }

    private void sendToSender(Sender<?> sender, String data, int dataBytes) {
        if (sender.isLimitReached()) {
            return;
        }

        try {
            sender.sendData(data);
            sender.increaseCount();
            sender.addBytes(dataBytes);
        } catch (Exception e) {
            log.error("Failed to send data to sender: {}", sender.getSenderName(), e);
        }
    }

    private String generate(Template vTemplate, Map<String, Object> data) {
        VelocityContext context = new VelocityContext();
        data.forEach((key, value) -> {
            if (value != null) {
                context.put(key, value);
            }
        });

        StringWriter writer = new StringWriter();
        vTemplate.merge(context, writer);
        return writer.toString();
    }

    public long[] getStepCounts() {
        return stepCounts != null ? stepCounts.clone() : new long[0];
    }

    public void attachRunningTask(Future<?> runningTask) {
        this.runningTask = runningTask;
        if (interrupted && runningTask != null) {
            runningTask.cancel(true);
        }
    }

    public void interrupt() {
        interrupted = true;
        Future<?> task = runningTask;
        if (task != null) {
            task.cancel(true);
        }
    }
}

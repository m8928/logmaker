package me.blueat.logmaker.core.scenario;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.log.LogService;
import me.blueat.logmaker.core.log.LogThread;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.core.util.VelocityTemplateUtil;
import me.blueat.logmaker.plugin.api.sender.Sender;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.StringWriter;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Getter
public class ScenarioThread implements Runnable {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MakerService makerService;
    private final SenderService senderService;
    private final LogService logService;
    private final ScenarioDto scenarioDto;
    private final VelocityEngine ve;

    private final AtomicLong count = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger currentStep = new AtomicInteger(-1);
    private final AtomicInteger currentLoop = new AtomicInteger(0);
    private volatile long[] stepCounts;
    private volatile Thread runningThread;

    private record StepTemplate(LogThread logThread, Template template) {
    }

    public ScenarioThread(MakerService makerService, SenderService senderService,
                          LogService logService, ScenarioDto scenarioDto) {
        this.makerService = makerService;
        this.senderService = senderService;
        this.logService = logService;
        this.scenarioDto = scenarioDto;
        this.ve = VelocityTemplateUtil.createSecureEngine(1);
    }

    @Override
    public void run() {
        running.set(true);
        runningThread = Thread.currentThread();
        List<ScenarioStepDto> steps = scenarioDto.getSteps() != null ? scenarioDto.getSteps() : Collections.emptyList();
        stepCounts = new long[steps.size()];

        Map<String, Sender<?>> senders = resolveSenders(steps);

        try {
            executeLoops(steps, senders);
        } finally {
            senders.values().forEach(Sender::decreaseRef);
            running.set(false);
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
            loop = nextLoop(infinite, loop);
            sleepBetweenLoops(infinite, loop, loopCount, intervalMinMs, intervalMaxMs);
        }
    }

    private boolean shouldRunLoop(boolean infinite, int loop, int loopCount) {
        return !Thread.currentThread().isInterrupted() && (infinite || loop < loopCount);
    }

    private int nextLoop(boolean infinite, int loop) {
        return infinite ? loop : loop + 1;
    }

    private void sleepBetweenLoops(boolean infinite, int loop, int loopCount,
                                   long intervalMinMs, long intervalMaxMs) {
        if (shouldRunLoop(infinite, loop, loopCount)) {
            sleepMillis(randomLong(intervalMinMs, intervalMaxMs));
        }
    }

    private void executeSteps(List<ScenarioStepDto> steps, Map<String, Sender<?>> senders,
                              Map<String, String> resolvedVars) {
        for (int stepIdx = 0; stepIdx < steps.size() && !Thread.currentThread().isInterrupted(); stepIdx++) {
            executeStep(steps.get(stepIdx), stepIdx, senders, resolvedVars);
        }
    }

    private void executeStep(ScenarioStepDto step, int stepIdx, Map<String, Sender<?>> senders,
                             Map<String, String> resolvedVars) {
        currentStep.set(stepIdx + 1);
        Optional<StepTemplate> stepTemplate = prepareStepTemplate(step, resolvedVars);
        stepTemplate.ifPresent(template -> executeRepeats(step, stepIdx, senders, resolvedVars, template));
    }

    private Optional<StepTemplate> prepareStepTemplate(ScenarioStepDto step, Map<String, String> resolvedVars) {
        LogThread logThread = logService.getLog(step.getLogName());
        if (logThread == null) {
            log.warn("Scenario step references unknown log: {}", step.getLogName());
            return Optional.empty();
        }

        String vFormat = buildVFormat(logThread, step, resolvedVars);
        Template vTemplate = compileTemplate(step.getLogName(), vFormat);
        if (vTemplate == null) {
            log.warn("Failed to compile template for log: {}", step.getLogName());
            return Optional.empty();
        }

        return Optional.of(new StepTemplate(logThread, vTemplate));
    }

    private void executeRepeats(ScenarioStepDto step, int stepIdx, Map<String, Sender<?>> senders,
                                Map<String, String> resolvedVars, StepTemplate stepTemplate) {
        for (int repeat = 0; repeat < step.getRepeat() && !Thread.currentThread().isInterrupted(); repeat++) {
            if (!sleepMillis(randomLong(step.getDelayMinMs(), step.getDelayMaxMs()))) {
                return;
            }
            sendStepData(step, stepIdx, senders, resolvedVars, stepTemplate);
        }
    }

    private void sendStepData(ScenarioStepDto step, int stepIdx, Map<String, Sender<?>> senders,
                              Map<String, String> resolvedVars, StepTemplate stepTemplate) {
        Map<String, Object> templateData = buildTemplateData(stepTemplate.logThread(), resolvedVars);
        String data = generate(stepTemplate.template(), templateData);
        int dataBytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

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

    private Map<String, Sender<?>> resolveSenders(List<ScenarioStepDto> steps) {
        Map<String, Sender<?>> senders = new ConcurrentHashMap<>();
        Set<String> senderNames = new LinkedHashSet<>();

        steps.forEach(step -> addSenderNames(senderNames, step.getSenders()));

        senderNames.forEach(senderName ->
                senderService.getSender(senderName).ifPresent(entry -> {
                    entry.getValue().increaseRef();
                    senders.put(senderName, entry.getValue());
                })
        );
        return senders;
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

    private long randomLong(long min, long max) {
        if (min >= max) return min;
        return min + RANDOM.nextLong(max - min + 1);
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

    private String buildVFormat(LogThread logThread, ScenarioStepDto step, Map<String, String> resolvedVars) {
        String vFormat = logThread.getVFormat();

        // Apply overrides: replace ${varName} with override value
        for (Map.Entry<String, String> override : stepOverrides(step).entrySet()) {
            vFormat = vFormat.replace("${" + override.getKey() + "}", override.getValue());
        }

        // Apply shared variables: replace ${varName} with resolved value
        for (Map.Entry<String, String> sharedVar : resolvedVars.entrySet()) {
            vFormat = vFormat.replace("${" + sharedVar.getKey() + "}", sharedVar.getValue());
        }

        return vFormat;
    }

    private Map<String, String> sharedVariables() {
        Map<String, String> variables = scenarioDto.getSharedVariables();
        return variables != null ? variables : Collections.emptyMap();
    }

    private Map<String, String> stepOverrides(ScenarioStepDto step) {
        Map<String, String> overrides = step.getOverrides();
        return overrides != null ? overrides : Collections.emptyMap();
    }

    private Map<String, Object> buildTemplateData(LogThread logThread, Map<String, String> resolvedVars) {
        Map<String, Object> data = new HashMap<>();

        // Get live maker data for remaining (non-overridden, non-shared) variables
        logThread.getMakerName().forEach(makerName ->
                makerService.getMaker(makerName).ifPresent(entry ->
                        data.put(makerName, entry.getValue().getData())
                )
        );

        // Shared variables override maker data
        data.putAll(resolvedVars);

        return data;
    }

    private Template compileTemplate(String name, String vFormat) {
        try {
            return VelocityTemplateUtil.compile(ve, name, vFormat);
        } catch (Exception e) {
            log.error("Failed to compile scenario template for log: {}", name, e);
            return null;
        }
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

    public void interrupt() {
        Thread t = runningThread;
        if (t != null) {
            t.interrupt();
        }
    }
}

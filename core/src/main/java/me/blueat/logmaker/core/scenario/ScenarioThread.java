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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private final VelocityEngine ve;

    private final AtomicLong count = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger currentStep = new AtomicInteger(-1);
    private final AtomicInteger currentLoop = new AtomicInteger(0);
    private volatile long[] stepCounts;
    private volatile Thread runningThread;

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
            int loopCount = scenarioDto.getLoopCount();
            long intervalMinMs = scenarioDto.getIntervalMinMs();
            long intervalMaxMs = scenarioDto.getIntervalMaxMs();
            boolean infinite = (loopCount == 0);
            int loop = 0;

            while (!Thread.currentThread().isInterrupted() && (infinite || loop < loopCount)) {
                currentLoop.set(loop + 1);
                // Re-resolve shared variables each loop
                Map<String, String> resolvedVars = resolveSharedVariables();

                int stepIdx = 0;
                for (ScenarioStepDto step : steps) {
                    currentStep.set(stepIdx + 1);
                    if (Thread.currentThread().isInterrupted()) break;

                    LogThread logThread = logService.getLog(step.getLogName());
                    if (logThread == null) {
                        log.warn("Scenario step references unknown log: {}", step.getLogName());
                        continue;
                    }

                    // Build format string: start from the log's vFormat, apply overrides and shared vars
                    String vFormat = buildVFormat(logThread, step, resolvedVars);
                    Template vTemplate = compileTemplate(step.getLogName(), vFormat);

                    if (vTemplate == null) {
                        log.warn("Failed to compile template for log: {}", step.getLogName());
                        continue;
                    }

                    for (int r = 0; r < step.getRepeat(); r++) {
                        if (Thread.currentThread().isInterrupted()) break;

                        // Random delay before each repeat execution
                        long delayMs = randomLong(step.getDelayMinMs(), step.getDelayMaxMs());
                        if (delayMs > 0) {
                            try {
                                Thread.sleep(delayMs);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }

                        if (Thread.currentThread().isInterrupted()) break;

                        Map<String, Object> templateData = buildTemplateData(logThread, resolvedVars);
                        String data = generate(vTemplate, templateData);

                        final int dataBytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                        getSendersForStep(senders, step).forEach(sender -> sendToSender(sender, data, dataBytes));
                        count.incrementAndGet();
                        if (stepIdx < stepCounts.length) stepCounts[stepIdx]++;
                    }
                    stepIdx++;
                }

                if (!infinite) {
                    loop++;
                }

                // Sleep random interval between loops
                if (!Thread.currentThread().isInterrupted() && (infinite || loop < loopCount)) {
                    long sleepMs = randomLong(intervalMinMs, intervalMaxMs);
                    if (sleepMs > 0) {
                        try {
                            Thread.sleep(sleepMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        } finally {
            senders.values().forEach(Sender::decreaseRef);
            running.set(false);
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
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private Map<String, String> resolveSharedVariables() {
        Map<String, String> resolved = new HashMap<>();
        scenarioDto.getSharedVariables().forEach((varName, makerName) ->
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
        for (Map.Entry<String, String> override : step.getOverrides().entrySet()) {
            vFormat = vFormat.replace("${" + override.getKey() + "}", override.getValue());
        }

        // Apply shared variables: replace ${varName} with resolved value
        for (Map.Entry<String, String> sharedVar : resolvedVars.entrySet()) {
            vFormat = vFormat.replace("${" + sharedVar.getKey() + "}", sharedVar.getValue());
        }

        return vFormat;
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

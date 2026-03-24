package me.blueat.logmaker.core.scenario;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.log.LogService;
import me.blueat.logmaker.core.log.LogThread;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.plugin.api.sender.Sender;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.node.SimpleNode;

import java.io.StringReader;
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

    private final AtomicLong count = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger currentStep = new AtomicInteger(-1);
    private final AtomicInteger currentLoop = new AtomicInteger(0);
    private volatile Thread runningThread;

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
        runningThread = Thread.currentThread();

        // Resolve senders
        Map<String, Sender<?>> senders = new ConcurrentHashMap<>();
        scenarioDto.getSenders().forEach(senderName ->
                senderService.getSender(senderName).ifPresent(entry -> {
                    entry.getValue().increaseRef();
                    senders.put(senderName, entry.getValue());
                })
        );

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
                for (ScenarioStepDto step : scenarioDto.getSteps()) {
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

                        senders.values().forEach(sender -> {
                            sender.sendData(data);
                            sender.increaseCount();
                        });
                        count.incrementAndGet();
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
            VelocityEngine ve = new VelocityEngine();
            ve.setProperty("runtime.introspector.uberspect", "org.apache.velocity.util.introspection.SecureUberspector");
            ve.setProperty("introspector.restrict.packages", "java.lang.reflect,java.lang.Runtime,java.lang.Process,java.lang.System");
            ve.setProperty("introspector.restrict.classes", "java.lang.Class,java.lang.ClassLoader,java.lang.Thread,java.lang.Compiler,java.lang.Runtime,java.lang.System");
            ve.setProperty("parser.pool.size", 1);
            ve.init();

            RuntimeServices rs = RuntimeSingleton.getRuntimeServices();
            StringReader sr = new StringReader(vFormat);

            Template vTemplate = new Template();
            vTemplate.setName(name);
            vTemplate.setRuntimeServices(rs);

            SimpleNode sn = null;
            try {
                sn = rs.parse(sr, vTemplate);
            } catch (ParseException e) {
                log.error("Scenario template parsing error for log: {}", name, e);
                return null;
            }

            vTemplate.setData(sn);
            vTemplate.initDocument();
            return vTemplate;
        } catch (Exception e) {
            log.error("Failed to compile scenario template for log: {}", name, e);
            return null;
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

    public void interrupt() {
        Thread t = runningThread;
        if (t != null) {
            t.interrupt();
        }
    }
}

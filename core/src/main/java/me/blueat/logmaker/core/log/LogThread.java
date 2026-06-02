package me.blueat.logmaker.core.log;

import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.core.util.VelocityTemplateUtil;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.sender.Sender;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.compiler.STLexer;

import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Getter
public class LogThread implements Runnable {
    private static final int BATCH_SIZE = 1000;

    private Instant start = null;

    private final AtomicLong count = new AtomicLong(0);
    private final AtomicLong bytes = new AtomicLong(0);
    private volatile long lastSecondEvents = 0;
    private volatile long lastSecondBytes = 0;
    private Set<String> makerName;
    private List<String> senderName;
    private final VelocityEngine ve;
    private Template vTemplate;
    private String vFormat;
    private final MakerService makerService;
    private final SenderService senderService;
    private Map<String, Sender<?>> senders;
    private Map<String, Maker<?>> makers;

    private final Lock updateLock = new ReentrantLock(true);

    private LogDto logDto;

    private volatile Thread runningThread;
    private long eventTargetRemainder = 0L;
    private long byteTargetRemainder = 0L;

    private record GenerationStats(long events, long bytes) {
    }

    public LogThread(MakerService makerService, SenderService senderService, LogDto logDto) {
        this.makerService = makerService;
        this.senderService = senderService;
        this.logDto = logDto;
        this.senders = new ConcurrentHashMap<>();
        this.makers = new ConcurrentHashMap<>();
        this.ve = VelocityTemplateUtil.createSecureEngine(20);
        init();
    }

    private void init() {
        this.senderName = logDto.getSender();
        ST template = new ST(logDto.getFormat());
        makerName = new HashSet<>();
        TokenStream tokens = template.impl.tokens;

        for (int i = 0; i < tokens.range(); i++) {
            Token token = tokens.get(i);
            if (token.getType() == STLexer.ID) {
                makerName.add(token.getText());
            }
        }

        for (String string : makerName) {
            template.add(string, "${" + string + "}");
        }

        this.vFormat = template.render();
        try {
            vTemplate = VelocityTemplateUtil.compile(ve, logDto.getName(), vFormat);
        } catch (Exception e) {
            log.error("Template parsing failed: {}", logDto.getName(), e);
            throw new IllegalStateException("Template parsing failed", e);
        }

        if (!makerService.getMakerNames().containsAll(makerName)) {
            makerName.removeAll(makerService.getMakerNames());
            throw new IllegalStateException("maker not found. " + makerName);
        }

        if (!senderService.getSenderNames().containsAll(senderName)) {
            senderName.removeAll(senderService.getSenderNames());
            throw new IllegalStateException("sender not found. " + makerName);
        }

        senderName.forEach(s -> senderService.getSender(s).ifPresent(o -> {
            o.getValue().increaseRef();
            senders.put(s, o.getValue());
        }));

        makerName.forEach(m -> makerService.getMaker(m).ifPresent(o -> {
            o.getValue().increaseRef();
            makers.put(m, o.getValue());
        }));
    }

    private Map<String, Object> getTemplateData() {
        Map<String, Object> result = Maps.newHashMap();

        for (String key : makerName) {
            result.put(key, makers.get(key).getData());
        }

        return result;
    }

    @Override
    public void run() {
        runningThread = Thread.currentThread();
        start = Instant.now();
        while (!Thread.currentThread().isInterrupted()) {
            Instant currentStart = Instant.now();
            updateSecondMetrics(currentStart);
            sleepUntilNextSecond(currentStart);
        }
    }

    private void updateSecondMetrics(Instant currentStart) {
        if (senders.isEmpty() || logDto.isPaused()) {
            recordLastSecond(new GenerationStats(0, 0));
            return;
        }

        boolean bytesMode = "bytes".equals(logDto.getEpsUnit());
        long targetUnits = nextTargetUnits(bytesMode, logDto.getEps(), targetDivisor(logDto.getEpsTimeUnit()));
        recordLastSecond(generateForCurrentSecond(currentStart, bytesMode, targetUnits));
    }

    private long targetDivisor(String timeUnit) {
        if (timeUnit == null) {
            return 1;
        }
        return switch (timeUnit) {
            case "min" -> 60;
            case "hour" -> 3600;
            case "day" -> 86400;
            default -> 1;
        };
    }

    private GenerationStats generateForCurrentSecond(Instant currentStart, boolean bytesMode, long targetUnits) {
        long secBytes = 0;
        long secEvents = 0;

        while (shouldGenerateMore(currentStart, bytesMode, secBytes, secEvents, targetUnits)) {
            GenerationStats batch = generateBatch(bytesMode, secBytes, secEvents, targetUnits);
            secBytes += batch.bytes();
            secEvents += batch.events();
        }

        return new GenerationStats(secEvents, secBytes);
    }

    private boolean shouldGenerateMore(Instant currentStart, boolean bytesMode,
                                       long secBytes, long secEvents, long targetUnits) {
        return !Thread.currentThread().isInterrupted()
                && isBelowTarget(bytesMode, secBytes, secEvents, targetUnits)
                && Duration.between(currentStart, Instant.now()).toMillis() < 1000;
    }

    private boolean isBelowTarget(boolean bytesMode, long secBytes, long secEvents, long targetUnits) {
        return bytesMode ? secBytes < targetUnits : secEvents < targetUnits;
    }

    private GenerationStats generateBatch(boolean bytesMode, long secBytes, long secEvents, long targetUnits) {
        long batchBytes = 0;
        long batchEvents = 0;

        updateLock.lock();
        try {
            for (int i = 0; i < BATCH_SIZE
                    && isBelowTarget(bytesMode, secBytes + batchBytes, secEvents + batchEvents, targetUnits); i++) {
                String data = generate(vTemplate, getTemplateData());
                int dataBytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                senders.values().forEach(sender -> sendToSender(sender, data, dataBytes));
                bytes.addAndGet(dataBytes);
                count.incrementAndGet();
                batchBytes += dataBytes;
                batchEvents++;
            }
        } finally {
            updateLock.unlock();
        }

        return new GenerationStats(batchEvents, batchBytes);
    }

    private void recordLastSecond(GenerationStats stats) {
        lastSecondEvents = stats.events();
        lastSecondBytes = stats.bytes();
    }

    private void sleepUntilNextSecond(Instant currentStart) {
        try {
            long processingTime = 1000 - Duration.between(currentStart, Instant.now()).toMillis();
            if (processingTime > 0) {
                Thread.sleep(processingTime);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    long nextTargetUnits(boolean bytesMode, long rawTarget, long divisor) {
        if (rawTarget <= 0 || divisor <= 0) {
            return 0;
        }

        if (bytesMode) {
            byteTargetRemainder += rawTarget;
            long target = byteTargetRemainder / divisor;
            byteTargetRemainder %= divisor;
            return target;
        }

        eventTargetRemainder += rawTarget;
        long target = eventTargetRemainder / divisor;
        eventTargetRemainder %= divisor;
        return target;
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

    public LogDto getLogDto() {
        this.logDto.setCount(count.get());
        this.logDto.setCurrentEps(lastSecondEvents);
        this.logDto.setBytes(bytes.get());
        this.logDto.setBytesPerSec(lastSecondBytes);
        this.logDto.setSample(getSample(this.vTemplate, this.getTemplateData()));
        return this.logDto;
    }

    private String getSample(Template vTemplate, Map<String, Object> data) {
        VelocityContext context = new VelocityContext();

        data.keySet().forEach(key -> {
            if (data.containsKey(key)) {
                context.put(key, data.get(key).toString());
            }
        });

        StringWriter writer = new StringWriter();
        vTemplate.merge(context, writer);

        return writer.toString();
    }

    public boolean updateLogDto(LogDto logDto) {
        boolean result;
        updateLock.lock();
        start = Instant.now();
        count.set(0);
        eventTargetRemainder = 0L;
        byteTargetRemainder = 0L;
        try {
            makerName.forEach(e -> makerService.getMaker(e).ifPresent(o -> {
                o.getValue().decreaseRef();
                makers.remove(e);
            }));

            senderName.forEach(s -> senderService.getSender(s).ifPresent(o -> {
                o.getValue().decreaseRef();
                senders.remove(s);
            }));

            LogDto backup = this.logDto;
            logDto.setPaused(backup.isPaused());
            this.logDto = logDto;

            try {
                init();
                result = true;
            }
            catch (Exception e) {
                log.error("Failed to update log configuration, reverting to backup", e);
                makerName.clear();
                makers.clear();
                senderName.clear();
                senders.clear();
                this.logDto = backup;
                result = false;
            }
        }
        finally {
            updateLock.unlock();
        }
        return result;
    }

    public String generate(Template vTemplate, Map<String, Object> data) {
        VelocityContext context = new VelocityContext();

        data.keySet().forEach(key -> {
            if (data.containsKey(key)) {
                context.put(key, data.get(key));
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

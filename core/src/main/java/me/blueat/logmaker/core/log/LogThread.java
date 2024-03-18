package me.blueat.logmaker.core.log;

import com.google.common.collect.Maps;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.sender.Sender;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.compiler.STLexer;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class LogThread extends Thread {
    private Instant start = null;

    private AtomicLong count = new AtomicLong(0);
    private Set<String> makerName;
    private List<String> senderName;
    private VelocityEngine ve;
    private Template vTemplate;
    private String vFormat;
    private MakerService makerService;
    private SenderService senderService;
    private Map<String, Sender<?>> senders;
    private Map<String, Maker<?>> makers;

    private Lock updateLock = new ReentrantLock(true);

    private LogDto logDto;

    public LogThread(MakerService makerService, SenderService senderService, LogDto logDto) {
        this.makerService = makerService;
        this.senderService = senderService;
        this.logDto = logDto;
        this.senders = new ConcurrentHashMap<>();
        this.makers = new ConcurrentHashMap<>();
        super.setName(logDto.getName());
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

        ve = new VelocityEngine();
        ve.setProperty("parser.pool.size", 20);
        ve.init();

        RuntimeServices rs = RuntimeSingleton.getRuntimeServices();
        StringReader sr = new StringReader(vFormat);
        SimpleNode sn = null;
        try {
            sn = rs.parse(sr, logDto.getName());
        } catch (ParseException e) {
            e.printStackTrace();
        }

        vTemplate = new Template();
        vTemplate.setRuntimeServices(rs);
        vTemplate.setData(sn);
        vTemplate.initDocument();

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

    @Override
    public synchronized void start() {
        super.start();
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
        start = Instant.now();
        AtomicLong createCount = new AtomicLong(0);
        while(!Thread.currentThread().isInterrupted()) {
            createCount.set(0);
            Instant currentStart = Instant.now();

            if (!senderName.isEmpty()) {
                updateLock.lock();
                try {
                    while (createCount.get() < logDto.getEps()) {
                        String data = generate(vTemplate, getTemplateData());

                        senders.values().forEach(sender -> {
                            sender.sendData(data);
                            sender.increaseCount();
                        });
                        createCount.incrementAndGet();
                        count.incrementAndGet();
                    }
                } finally {
                    updateLock.unlock();
                }
            }

            try {
                long processingTime = 1000 - Duration.between(currentStart, Instant.now()).toMillis();
                if (processingTime > 0) {
                    Thread.sleep(processingTime);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private long getCurrentEps() {
        Instant end = Instant.now();
        long timing = Duration.between(start, end).toMillis();

        if (timing > 0) {
            return Math.round((count.get() /(double)timing) * 1000);
        }
        else {
            return 0;
        }
    }

    public LogDto getLogDto() {
        this.logDto.setCount(count.get());
        this.logDto.setCurrentEps(getCurrentEps());
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
            this.logDto = logDto;

            try {
                init();
                result = true;
            }
            catch (Exception e) {
                makerName.clear();
                makers.clear();
                senderName.clear();
                senders.clear();
                this.logDto = backup;
                updateLogDto(this.logDto);
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

    @Override
    public void interrupt() {
        super.interrupt();
    }
}

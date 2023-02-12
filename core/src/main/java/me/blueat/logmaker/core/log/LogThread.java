package me.blueat.logmaker.core.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.sender.SenderService;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Data
public class LogThread extends Thread {
    private Instant start = null;

    public AtomicLong count = new AtomicLong(0);
    private Set<String> expressions;
    private List<String> sender;
    private VelocityEngine ve;
    private Template vTemplate;
    private String vFormat;
    private MakerService makerService;
    private SenderService senderService;

    private Lock updateLock = new ReentrantLock(true);

    private LogDto logDto;

    public LogThread(MakerService makerService, SenderService senderService, LogDto logDto) throws JsonProcessingException {
        this.makerService = makerService;
        this.senderService = senderService;
        this.logDto = logDto;
        super.setName(logDto.getName());
        init();
    }

    private void init() {
        this.sender = logDto.getSender();
        ST template = new ST(logDto.getFormat());
        expressions = new HashSet<>();
        TokenStream tokens = template.impl.tokens;

        for (int i = 0; i < tokens.range(); i++) {
            Token token = tokens.get(i);
            if (token.getType() == STLexer.ID) {
                expressions.add(token.getText());
            }
        }

        for (String string : expressions) {
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

        if (!makerService.getMakerNames().containsAll(expressions)) {
            expressions.removeAll(makerService.getMakerNames());
            throw new IllegalStateException("maker not found. " + expressions);
        }

        if (!senderService.getSenderNames().containsAll(sender)) {
            sender.removeAll(senderService.getSenderNames());
            throw new IllegalStateException("sender not found. " + expressions);
        }

        sender.forEach(s -> senderService.getSender(s).ifPresent(o -> o.getValue().increaseRef()));
        expressions.forEach(e -> makerService.getMaker(e).ifPresent(o -> o.getValue().increaseRef()));
    }

    @Override
    public void start() {
        super.start();
    }

    private Map<String, Object> templateData() {
        Map<String, Object> result = Maps.newHashMap();

        for (String key : expressions) {
            makerService.getMaker().stream().filter(m -> m.getName().equals(key)).findAny()
                    .ifPresent(p -> makerService.getMaker(key).ifPresent(v -> result.put(key, v.getValue().getData())));
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

            if (sender.size() > 0) {
                updateLock.lock();
                try {
                    while(createCount.get() < logDto.getEps()) {
                        sender.forEach(senderName ->  {
                            senderService.getSender(senderName).ifPresent(s -> {
                                s.getValue().sendData(vTemplate, templateData());
                                s.getValue().increaseCount();
                            });

                            createCount.incrementAndGet();
                            count.incrementAndGet();
                        });
                    }
                }
                finally {
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
        this.logDto.setSample(getSample(this.vTemplate, this.templateData()));
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
            expressions.forEach(e -> makerService.getMaker(e).ifPresent(o -> o.getValue().decreaseRef()));
            sender.forEach(s -> senderService.getSender(s).ifPresent(o -> o.getValue().decreaseRef()));
            LogDto backup = this.logDto;
            this.logDto = logDto;

            try {
                init();
                result = true;
            }
            catch (Exception e) {
                expressions.clear();
                sender.clear();
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

    @Override
    public void interrupt() {
        super.interrupt();
    }
}

package me.blueat.logmaker.core.loggen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.sender.Sender;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.compiler.STLexer;

import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class LogThread extends Thread {
    private final static ObjectMapper objectMapper = new ObjectMapper();

    private Instant start = null;

    public AtomicLong count = new AtomicLong(0);
    private final Set<String> expressions;
    private final List<String> senders;
    private VelocityEngine ve;
    private Template vTemplate;
    private String vFormat;
    private Map makerConcurrentHashMap = new ConcurrentHashMap<>();
    private MakerService makerService;
    private SenderService senderService;

    private LogDto logDto;

    public LogThread(MakerService makerService, SenderService senderService, LogDto logDto) throws JsonProcessingException {
        this.makerService = makerService;
        this.senderService = senderService;
        this.logDto = logDto;
        super.setName(logDto.getName());
        this.senders = logDto.getSenders();
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
            template.add(string, "$" + string);
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
    }

    @Override
    public void start() {
        Set<String> makerNames = new HashSet<>();
        makerNames.addAll(makerService.getMakerNames());

        if (makerNames.containsAll(expressions)) {
            super.start();
        }
        else {
            expressions.removeAll(makerNames);
            throw new IllegalStateException("maker not found. " + expressions);
        }
    }

    private Map<String, Object> templateData() {
        Map<String, Object> result = new HashMap<>();

        for (String string : expressions) {
            if (!makerConcurrentHashMap.containsKey(string)) {
                makerConcurrentHashMap.put(string, makerService.getMaker(string));
            }

            Maker<?> cacheMaker = (Maker<?>) makerConcurrentHashMap.get(string);

            if (cacheMaker != null) {
                result.put(string, cacheMaker.getData());
            }
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

            while(createCount.get() < logDto.getEps()) {
                senders.forEach(senderName ->  {
                    Sender sender = senderService.getSender(senderName);
                    if (sender != null) {
                        sender.sendData(vTemplate, templateData());
                    }
                });

                createCount.incrementAndGet();
                count.incrementAndGet();
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
        this.logDto.setSample(templateData());
        return this.logDto;
    }


    @Override
    public void interrupt() {
        super.interrupt();
    }
}

package me.blueat.logmaker.core.loggen;

import com.cloudbees.syslog.Facility;
import com.cloudbees.syslog.MessageFormat;
import com.cloudbees.syslog.Severity;
import com.cloudbees.syslog.sender.UdpSyslogMessageSender;

import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.plugin.api.Maker;
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

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class LogThread extends Thread {
    private Instant start = null;

    public AtomicLong count = new AtomicLong(0);
    private final Set<String> expressions;
    private final List<UdpSyslogMessageSender> messageSenderList;
    private VelocityEngine ve;
    private Template vTemplate;
    private String vFormat;
    private Map makerConcurrentHashMap = new ConcurrentHashMap<>();
    private MakerService makerService;

    private LogDto logDto;

    public LogThread(MakerService makerService, LogDto logDto) {
        this.makerService = makerService;
        this.logDto = logDto;
        super.setName(logDto.getName());
        this.messageSenderList = new LinkedList<>();
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

        for (String deviceIp : logDto.getDevices()) {
            for (SyslogDto syslogDto : logDto.getSyslog()) {
                Facility facility;

                try {
                    facility = Facility.fromNumericalCode(syslogDto.getFacility());
                }
                catch (IllegalArgumentException iae) {
                    facility = Facility.USER;
                }

                Severity severity;

                try {
                    severity = Severity.fromNumericalCode(syslogDto.getSeverity());
                }
                catch (IllegalArgumentException iae) {
                    severity = Severity.INFORMATIONAL;
                }

                MessageFormat messageFormat;

                try {
                    messageFormat = MessageFormat.valueOf(syslogDto.getMessageFormat());
                }
                catch (IllegalArgumentException iae) {
                    messageFormat = MessageFormat.RFC_3164;
                }

                UdpSyslogMessageSender udpSyslogMessageSender = new UdpSyslogMessageSender();
                udpSyslogMessageSender.setDefaultMessageHostname(logDto.getIpPrefix() + deviceIp);
                udpSyslogMessageSender.setDefaultAppName(logDto.getName());
                udpSyslogMessageSender.setDefaultFacility(facility);
                udpSyslogMessageSender.setDefaultSeverity(severity);
                udpSyslogMessageSender.setSyslogServerHostname(syslogDto.getIp());
                udpSyslogMessageSender.setSyslogServerPort(syslogDto.getPort());
                udpSyslogMessageSender.setMessageFormat(messageFormat);
                messageSenderList.add(udpSyslogMessageSender);
            }
        }
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

    private String generator(String deviceIp) {
        VelocityContext context = new VelocityContext();

        for (String string : expressions) {
            if (!makerConcurrentHashMap.containsKey(string)) {
                makerConcurrentHashMap.put(string, makerService.getMaker(string));
            }

            Maker<?> cacheMaker = (Maker<?>) makerConcurrentHashMap.get(string);

            if (cacheMaker != null) {
                context.put(string, cacheMaker.getData());
            }
            else {
                return null;
            }
            context.put("DEVICE_IP", deviceIp);
        }

        StringWriter writer = new StringWriter();
        vTemplate.merge(context, writer);

        return writer.toString();
    }

    @Override
    public void run() {
        start = Instant.now();
        AtomicLong createCount = new AtomicLong(0);
        while(!Thread.currentThread().isInterrupted()) {
            createCount.set(0);
            Instant currentStart = Instant.now();

            while(createCount.get() < logDto.getEps()) {
                try {
                    for (UdpSyslogMessageSender udpSyslogMessageSender : messageSenderList) {
                        String generateLog = generator(udpSyslogMessageSender.getDefaultMessageHostname().replace(logDto.getIpPrefix(), ""));
                        udpSyslogMessageSender.sendMessage(generateLog);
                    }
                } catch (IOException e) {}

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
        this.logDto.setSample(generator("0.0.0.0"));
        return this.logDto;
    }


    @Override
    public void interrupt() {
        for (UdpSyslogMessageSender udpSyslogMessageSender : messageSenderList) {
            try {
                udpSyslogMessageSender.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        super.interrupt();
    }
}

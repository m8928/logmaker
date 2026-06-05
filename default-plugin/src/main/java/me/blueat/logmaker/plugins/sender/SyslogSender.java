package me.blueat.logmaker.plugins.sender;

import com.cloudbees.syslog.Facility;
import com.cloudbees.syslog.MessageFormat;
import com.cloudbees.syslog.Severity;
import com.cloudbees.syslog.sender.UdpSyslogMessageSender;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.sender.Sender;
import me.blueat.logmaker.plugin.api.sender.SenderArgs;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class SyslogSender extends Sender<String> {
    private String name;
    private List<UdpSyslogMessageSender> udpSyslogMessageSenderList;
    private String ip;
    private int port;
    private int facility;
    private int severity;
    private String messageFormat;
    private List<String> host;
    private String hostPrefix;
    private Map<String, Object> args;
    private Lock updateLock = new ReentrantLock(true);

    public SyslogSender(String name, Map<String, Object> args) {
        this.name = name;
        this.args = args;
        init();
        initSyslogSender();
    }

    @SuppressWarnings("unchecked")
    public void init() {
        this.ip = SenderArgs.toString(args.get("ip"));
        this.port = SenderArgs.toInt(args.get("port"));
        this.facility = SenderArgs.toInt(args.getOrDefault("facility", 1));
        this.severity = SenderArgs.toInt(args.getOrDefault("severity", 6));
        this.messageFormat = SenderArgs.toString(args.getOrDefault("messageFormat", MessageFormat.RFC_3164));
        this.host = SenderArgs.toList(args.get("host"));
        this.hostPrefix = SenderArgs.toString(args.getOrDefault("hostPrefix", ""));
    }

    public void initSyslogSender() {
        udpSyslogMessageSenderList = new LinkedList<>();
        for (String deviceIp : host) {
            Facility facility;

            try {
                facility = Facility.fromNumericalCode(this.facility);
            }
            catch (IllegalArgumentException iae) {
                facility = Facility.USER;
            }

            Severity severity;

            try {
                severity = Severity.fromNumericalCode(this.severity);
            }
            catch (IllegalArgumentException iae) {
                severity = Severity.INFORMATIONAL;
            }

            MessageFormat messageFormat;

            try {
                messageFormat = MessageFormat.valueOf(this.messageFormat);
            }
            catch (IllegalArgumentException iae) {
                messageFormat = MessageFormat.RFC_5424;
            }

            UdpSyslogMessageSender udpSyslogMessageSender = new UdpSyslogMessageSender();
            udpSyslogMessageSender.setDefaultMessageHostname(this.hostPrefix + deviceIp);
            udpSyslogMessageSender.setDefaultAppName(this.getSenderName());
            udpSyslogMessageSender.setDefaultFacility(facility);
            udpSyslogMessageSender.setDefaultSeverity(severity);
            udpSyslogMessageSender.setSyslogServerHostname(this.ip);
            udpSyslogMessageSender.setSyslogServerPort(this.port);
            udpSyslogMessageSender.setMessageFormat(messageFormat);
            udpSyslogMessageSenderList.add(udpSyslogMessageSender);
        }
    }

    @Override
    public String getSenderName() {
        return this.name;
    }

    @Override
    public void sendData(String data) {
        updateLock.lock();
        try {
            udpSyslogMessageSenderList.forEach(sender -> {
                try {
                    sender.sendMessage(data);
                } catch (Exception e) {
                    log.warn("Failed to send syslog message", e);
                }
            });
        }
        finally {
            updateLock.unlock();
        }
    }

    @Override
    public String getType() {
        return "Syslog";
    }

    @Override
    public Thread getThread() {
        return null;
    }

    @Override
    public boolean isThread() {
        return false;
    }

    @Override
    public void update(Map<String, Object> args) {
        updateLock.lock();
        try {
            udpSyslogMessageSenderList.forEach(sender -> {
                try {
                    sender.close();
                } catch (Exception e) {
                }
            });
            udpSyslogMessageSenderList.clear();
            this.args = args;
            init();
            initSyslogSender();
        }
        finally {
            updateLock.unlock();
        }
    }
}

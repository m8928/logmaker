package me.blueat.logmaker.plugins.sender;

import com.cloudbees.syslog.Facility;
import com.cloudbees.syslog.MessageFormat;
import com.cloudbees.syslog.Severity;
import com.cloudbees.syslog.sender.UdpSyslogMessageSender;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.sender.Sender;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Slf4j
public class SyslogSender implements Sender<String> {

    private String name;
    private List<UdpSyslogMessageSender> udpSyslogMessageSenderList;
    private String ip;
    private int port;
    private int facility;
    private int severity;
    private String messageFormat;
    private List<String> hosts;
    private String hostPrefix;

    public SyslogSender(String name, String ip, int port, int facility, int severity, String messageFormat, List<String> hosts, String hostPrefix) {
        this.name = name;
        this.hosts = hosts;
        this.ip = ip;
        this.port = port;
        this.facility = facility;
        this.severity = severity;
        this.messageFormat = messageFormat;
        this.hostPrefix = hostPrefix;
        initSyslogSender();
    }

    public void initSyslogSender() {
        udpSyslogMessageSenderList = new LinkedList<>();
        for (String deviceIp : hosts) {
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
                messageFormat = MessageFormat.RFC_3164;
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
    public void sendData(Template vTemplate, Map<String, String> data) {
        udpSyslogMessageSenderList.forEach(sender -> {
            try {
                sender.sendMessage(generate(vTemplate, data));
            } catch (IOException e) {
                //NOTHING
            }
        });
    }

    @Override
    public String generate(Template vTemplate, Map<String, String> data) {
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
    public String getType() {
        return "Syslog";
    }

    @Override
    public boolean isThread() {
        return false;
    }
}

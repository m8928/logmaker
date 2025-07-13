package me.blueat.logmaker.plugins.sender;

import com.cloudbees.syslog.sender.UdpSyslogMessageSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SyslogSenderTest {

    private SyslogSender syslogSender;

    @Mock
    private List<UdpSyslogMessageSender> udpSyslogMessageSenderList;

    @Test
    void sendData() throws Exception {
        // Given
        Map<String, Object> args = new HashMap<>();
        args.put("ip", "localhost");
        args.put("port", 514);
        args.put("host", Collections.singletonList("testhost"));

        syslogSender = new SyslogSender("testSender", args);
        syslogSender.setUdpSyslogMessageSenderList(udpSyslogMessageSenderList);

        String testData = "This is a test message";

        // When
        syslogSender.sendData(testData);

        // Then
        verify(udpSyslogMessageSenderList).forEach(any());
    }
}

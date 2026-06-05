package me.blueat.logmaker.plugins.sender;

import com.cloudbees.syslog.sender.UdpSyslogMessageSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void sendData_actualMessage() throws Exception {
        // Given: real UdpSyslogMessageSender replaced with mock to capture message
        Map<String, Object> args = new HashMap<>();
        args.put("ip", "localhost");
        args.put("port", 514);
        args.put("host", Collections.singletonList("testhost"));

        syslogSender = new SyslogSender("testSender", args);

        UdpSyslogMessageSender mockUdpSender = org.mockito.Mockito.mock(UdpSyslogMessageSender.class);
        syslogSender.setUdpSyslogMessageSenderList(List.of(mockUdpSender));

        String testData = "Hello Syslog";

        // When
        syslogSender.sendData(testData);

        // Then: verify the actual message content was passed to sendMessage
        ArgumentCaptor<CharSequence> messageCaptor = ArgumentCaptor.forClass(CharSequence.class);
        verify(mockUdpSender).sendMessage(messageCaptor.capture());
        assertEquals(testData, messageCaptor.getValue().toString());
    }
}

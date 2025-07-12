package me.blueat.logmaker.plugins.sender;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugSenderTest {

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(DebugSender.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        listAppender.stop();
    }

    @Test
    void sendData() {
        // Given
        DebugSender debugSender = new DebugSender("testSender");
        String testData = "This is a test message";

        // When
        debugSender.sendData(testData);

        // Then
        assertEquals(1, listAppender.list.size());
        assertEquals(testData, listAppender.list.get(0).getFormattedMessage());
    }
}

package me.blueat.logmaker.plugins.sender;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaSenderTest {

    private KafkaSender kafkaSender;

    @Mock
    private KafkaProducer<String, byte[]> producer;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, byte[]>> recordCaptor;

    @Test
    void sendData() {
        // Given
        Map<String, Object> args = new HashMap<>();
        args.put("topic", "test-topic");
        args.put("index", "test-index");
        args.put("indexPattern", "yyyyMMdd");
        args.put("bootstrap", "localhost:9092");

        kafkaSender = new KafkaSender("testSender", args);
        kafkaSender.setProducer(producer);

        String testData = "This is a test message";

        // When
        kafkaSender.sendData(testData);

        // Then
        verify(producer).send(recordCaptor.capture());
        ProducerRecord<String, byte[]> capturedRecord = recordCaptor.getValue();
        assertEquals("test-topic", capturedRecord.topic());
        assertEquals(testData, new String(capturedRecord.value()));
    }
}

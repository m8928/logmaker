package me.blueat.logmaker.plugins.sender;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.sender.Sender;
import me.blueat.logmaker.plugin.api.sender.SenderArgs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Data
@Slf4j
public class KafkaSender extends Sender<String> {

    private String name;
    private Lock updateLock;
    private KafkaProducer<String, String> producer;
    private String topic;
    private Map<String, Object> args;

    public KafkaSender(String name, Map<String, Object> args) {
        this.name = name;
        this.updateLock = new ReentrantLock();
        this.args = args;
        init();
    }
    public void init() {
        this.topic = SenderArgs.toString(args.get("topic"));
        Properties prop = new Properties();
        prop.put("bootstrap.servers", SenderArgs.toString(args.get("bootstrap"))); // server, kafka host
        prop.put("key.serializer", StringSerializer.class);
        prop.put("value.serializer", StringSerializer.class);
        prop.put("acks", "all");
        prop.put("block.on.buffer.full", "true");
        producer = new KafkaProducer<>(prop);
    }


    @Override
    public String getSenderName() {
        return this.name;
    }

    @Override
    public void sendData(String data) {
        updateLock.lock();
        try {
            producer.send(new ProducerRecord<>(topic, data));
        }
        finally {
            updateLock.unlock();
        }
    }

    @Override
    public String getType() {
        return "Kafka";
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
            producer.close();
            this.args = args;
            init();
        }
        finally {
            updateLock.unlock();
        }
    }
}

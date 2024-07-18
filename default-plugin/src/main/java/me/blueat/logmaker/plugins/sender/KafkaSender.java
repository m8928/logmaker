package me.blueat.logmaker.plugins.sender;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.sender.Sender;
import me.blueat.logmaker.plugin.api.sender.SenderArgs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class KafkaSender extends Sender<String> {

    private String name;
    private Lock updateLock;
    private KafkaProducer<String, byte[]> producer;
    private String topic;
    private String index;
    private Map<String, Object> args;
    private DateTimeFormatter dateTimeFormatter;
    private DateTimeFormatter readTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public KafkaSender(String name, Map<String, Object> args) {
        this.name = name;
        this.updateLock = new ReentrantLock();
        this.args = args;
        init();
    }
    public void init() {
        this.topic = SenderArgs.toString(args.get("topic"));
        this.index = SenderArgs.toString(args.get("index"));
        this.dateTimeFormatter = DateTimeFormatter.ofPattern(SenderArgs.toString(args.get("indexPattern")));

        Properties prop = new Properties();
        prop.put("bootstrap.servers", SenderArgs.toString(args.get("bootstrap"))); // server, kafka host
        prop.put("key.serializer", StringSerializer.class);
        prop.put("value.serializer", ByteArraySerializer.class);
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
            ProducerRecord<String, byte[]> producerRecord = new ProducerRecord<>(topic, data.getBytes());
            producerRecord.headers().add("index", this.index.concat(dateTimeFormatter.format(LocalDateTime.now())).getBytes());
            producerRecord.headers().add("read_time", readTimeFormatter.format(LocalDateTime.now()).getBytes());
            producerRecord.headers().add("module", "LogMaker".getBytes());
            producerRecord.headers().add("pointer", "0".getBytes());
            producerRecord.headers().add("file_name", this.getName().getBytes());
            producerRecord.headers().add("parser_name", this.getName().getBytes());

            producer.send(producerRecord);
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

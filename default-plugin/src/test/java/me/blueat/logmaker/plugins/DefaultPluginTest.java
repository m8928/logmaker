package me.blueat.logmaker.plugins;

import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultPluginTest {

    @Test
    void dateMaker() throws ArgumentsNotValidException {
        DefaultPlugin.DateMakerFactory factory = new DefaultPlugin.DateMakerFactory();
        Map<String, Object> args = new HashMap<>();
        args.put("format", "yyyy-MM-dd");
        assertNotNull(factory.getMaker("test", args));
    }

    @Test
    void ipMaker() throws ArgumentsNotValidException {
        DefaultPlugin.IPFactory factory = new DefaultPlugin.IPFactory();
        assertNotNull(factory.getMaker("test", Collections.emptyMap()));
    }

    @Test
    void ipRangeMaker() throws ArgumentsNotValidException {
        DefaultPlugin.IPRangeFactory factory = new DefaultPlugin.IPRangeFactory();
        Map<String, Object> args = new HashMap<>();
        args.put("start", "192.168.0.1");
        args.put("end", "192.168.0.10");
        args.put("deviation", 1);
        assertNotNull(factory.getMaker("test", args));
    }

    @Test
    void numberRangeMaker() throws ArgumentsNotValidException {
        DefaultPlugin.NumberRangeMakerFactory factory = new DefaultPlugin.NumberRangeMakerFactory();
        Map<String, Object> args = new HashMap<>();
        args.put("start", 1);
        args.put("end", 10);
        assertNotNull(factory.getMaker("test", args));
    }

    @Test
    void pickMaker() throws ArgumentsNotValidException {
        DefaultPlugin.PickMakerFactory factory = new DefaultPlugin.PickMakerFactory();
        Map<String, Object> args = new HashMap<>();
        args.put("picker", new ArrayList<>(Collections.singletonList("a")));
        assertNotNull(factory.getMaker("test", args));
    }

    @Test
    void regexMaker() throws ArgumentsNotValidException {
        DefaultPlugin.RegexMakerFactory factory = new DefaultPlugin.RegexMakerFactory();
        Map<String, Object> args = new HashMap<>();
        args.put("regex", ".*");
        assertNotNull(factory.getMaker("test", args));
    }

    @Test
    void uuidMaker() throws ArgumentsNotValidException {
        DefaultPlugin.UUIDFactory factory = new DefaultPlugin.UUIDFactory();
        assertNotNull(factory.getMaker("test", Collections.emptyMap()));
    }

    @Test
    void debugSender() throws ArgumentsNotValidException {
        DefaultPlugin.DebugSenderFactory factory = new DefaultPlugin.DebugSenderFactory();
        assertNotNull(factory.getSender("test", Collections.emptyMap()));
    }

    @Test
    void kafkaSender() throws ArgumentsNotValidException {
        DefaultPlugin.KafkaSenderFactory factory = new DefaultPlugin.KafkaSenderFactory();
        Map<String, Object> args = new HashMap<>();
        args.put("bootstrap", "localhost:9092");
        args.put("topic", "test");
        args.put("index", "test");
        args.put("indexPattern", "yyyyMMdd");
        assertNotNull(factory.getSender("test", args));
    }

    @Test
    void syslogSender() throws ArgumentsNotValidException {
        DefaultPlugin.SyslogSenderFactory factory = new DefaultPlugin.SyslogSenderFactory();
        Map<String, Object> args = new HashMap<>();
        args.put("ip", "localhost");
        args.put("port", 514);
        args.put("host", new ArrayList<>(Collections.singletonList("localhost")));
        assertNotNull(factory.getSender("test", args));
    }
}

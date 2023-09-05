package me.blueat.logmaker.plugins;

import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerArgs;
import me.blueat.logmaker.plugin.api.maker.MakerPlugin;
import me.blueat.logmaker.plugin.api.sender.Sender;
import me.blueat.logmaker.plugin.api.sender.SenderArgs;
import me.blueat.logmaker.plugin.api.sender.SenderPlugin;
import me.blueat.logmaker.plugins.maker.*;
import me.blueat.logmaker.plugins.sender.DebugSender;
import me.blueat.logmaker.plugins.sender.KafkaSender;
import me.blueat.logmaker.plugins.sender.SyslogSender;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class DefaultPlugin extends Plugin {
    public DefaultPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("logmaker default plugin start!");
    }

    @Override
    public void stop() {
        log.info("logmaker default plugin stop!");
    }

    @Extension
    public static class RegexMakerFactory extends MakerPlugin {
        static Map<String, MakerArgs> makerArgsMap = new LinkedHashMap<>();

        static {
            makerArgsMap.put("regex", new MakerArgs(String.class, "", true));
        }

        public String getType() {
            return "Regex";
        }

        @Override
        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(makerArgsMap, args)) {
                return new RegexMaker(name, getType(), args);
            }
            else {
                return null;
            }
        }

        @Override
        public Map<String, MakerArgs> getMakerArgsMap() {
            return makerArgsMap;
        }
    }

    @Extension
    public static class PickMakerFactory extends MakerPlugin {
        static Map<String, MakerArgs> makerArgsMap = new LinkedHashMap<>();

        static {
            makerArgsMap.put("picker", new MakerArgs(ArrayList.class, "", true));
        }

        public String getType() {
            return "Pick";
        }

        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(makerArgsMap, args)) {
                return new PickMaker(name, getType(), args);
            }
            else {
                return null;
            }
        }

        @Override
        public Map<String, MakerArgs> getMakerArgsMap() {
            return makerArgsMap;
        }
    }

    @Extension
    public static class NumberRangeMakerFactory extends MakerPlugin {
        static Map<String, MakerArgs> makerArgsMap = new LinkedHashMap<>();

        static {
            makerArgsMap.put("start", new MakerArgs(Number.class, "The smallest number returned.", true));
            makerArgsMap.put("end", new MakerArgs(Number.class, "The largest number returned.", true));
            makerArgsMap.put("random", new MakerArgs(Boolean.class, "", false));
        }

        public String getType() {
            return "NumberRange";
        }

        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(makerArgsMap, args)) {
                return new NumberRangeMaker(name, getType(), args);
            }
            else {
                return null;
            }
        }

        @Override
        public Map<String, MakerArgs> getMakerArgsMap() {
            return makerArgsMap;
        }
    }

    @Extension
    public static class DateMakerFactory extends MakerPlugin {
        static Map<String, MakerArgs> makerArgsMap = new LinkedHashMap<>();

        static {
            makerArgsMap.put("format", new MakerArgs(String.class, "", true));
        }

        public String getType() {
            return "Date";
        }

        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(makerArgsMap, args)) {
                return new DateMaker(name, getType(), args);
            }
            else {
                return null;
            }
        }

        @Override
        public Map<String, MakerArgs> getMakerArgsMap() {
            return makerArgsMap;
        }
    }

    @Extension
    public static class UUIDFactory extends MakerPlugin {
        static Map<String, MakerArgs> makerArgsMap = new LinkedHashMap<>();

        static {
        }

        public String getType() {
            return "UUID";
        }

        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            return new UUIDMaker(name, getType());
        }

        @Override
        public Map<String, MakerArgs> getMakerArgsMap() {
            return makerArgsMap;
        }
    }

    @Extension
    public static class IPFactory extends MakerPlugin {
        static Map<String, MakerArgs> makerArgsMap = new LinkedHashMap<>();

        static {
        }

        public String getType() {
            return "IP";
        }

        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            return new IPMaker(name, getType());
        }

        @Override
        public Map<String, MakerArgs> getMakerArgsMap() {
            return makerArgsMap;
        }
    }

    @Extension
    public static class IPRangeFactory extends MakerPlugin {
        static Map<String, MakerArgs> makerArgsMap = new LinkedHashMap<>();

        static {
            makerArgsMap.put("start", new MakerArgs(String.class, "The smallest number returned.", true));
            makerArgsMap.put("end", new MakerArgs(String.class, "The largest number returned.", true));
            makerArgsMap.put("deviation", new MakerArgs(Number.class, "", true));
        }

        public String getType() {
            return "IPRange";
        }

        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(makerArgsMap, args)) {
                return new IPRangeMaker(name, getType(), args);
            }
            else {
                return null;
            }
        }

        @Override
        public Map<String, MakerArgs> getMakerArgsMap() {
            return makerArgsMap;
        }
    }

    @Extension
    public static class SyslogSenderFactory extends SenderPlugin {
        static Map<String, SenderArgs> senderArgsMap = new LinkedHashMap<>();

        static {
            senderArgsMap.put("ip", new SenderArgs(String.class, "", true));
            senderArgsMap.put("port", new SenderArgs(Integer.class, "", true));
            senderArgsMap.put("facility", new SenderArgs(Integer.class, "", false));
            senderArgsMap.put("severity", new SenderArgs(Integer.class, "", false));
            senderArgsMap.put("messageFormat", new SenderArgs(String.class, "", false));
            senderArgsMap.put("host", new SenderArgs(ArrayList.class, "", true));
            senderArgsMap.put("hostPrefix", new SenderArgs(String.class, "", false));
        }

        public String getType() {
            return "Syslog";
        }

        @Override
        public Sender getSender(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(senderArgsMap, args)) {
                return new SyslogSender(name, args);
            }
            else {
                return null;
            }
        }

        @Override
        public Map<String, SenderArgs> getSenderArgsMap() {
            return senderArgsMap;
        }
    }

    @Extension
    public static class KafkaSenderFactory extends SenderPlugin {
        static Map<String, SenderArgs> senderArgsMap = new LinkedHashMap<>();

        static {
            senderArgsMap.put("bootstrap", new SenderArgs(String.class, "", true));
            senderArgsMap.put("topic", new SenderArgs(String.class, "", true));
            senderArgsMap.put("index", new SenderArgs(String.class, "", true));
            senderArgsMap.put("indexPattern", new SenderArgs(String.class, "", true));
        }

        public String getType() {
            return "Kafka";
        }

        @Override
        public Sender getSender(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(senderArgsMap, args)) {
                return new KafkaSender(name, args);
            }
            else {
                return null;
            }
        }

        @Override
        public Map<String, SenderArgs> getSenderArgsMap() {
            return senderArgsMap;
        }
    }

    @Extension
    public static class DebugSenderFactory extends SenderPlugin {
        static Map<String, SenderArgs> senderArgsMap = new LinkedHashMap<>();

        static {
        }

        public String getType() {
            return "Debug";
        }

        @Override
        public Sender getSender(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(senderArgsMap, args)) {
                return new DebugSender(name);
            }
            else {
                return null;
            }
        }

        @Override
        public Map<String, SenderArgs> getSenderArgsMap() {
            return senderArgsMap;
        }
    }
}

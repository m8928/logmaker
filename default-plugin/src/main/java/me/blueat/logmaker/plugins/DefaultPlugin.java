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
            makerArgsMap.put("name", new MakerArgs(String.class, ""));
            makerArgsMap.put("regex", new MakerArgs(String.class, ""));
        }

        public String getType() {
            return "Regex";
        }

        @Override
        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(makerArgsMap, args)) {
                return new RegexMaker(name, MakerArgs.toString(args.get("regex")));
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
            makerArgsMap.put("name", new MakerArgs(String.class, ""));
            makerArgsMap.put("picker", new MakerArgs(ArrayList.class, ""));
        }

        public String getType() {
            return "Pick";
        }

        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(makerArgsMap, args)) {
                return new PickMaker(name, MakerArgs.toList(args.get("picker")));
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
            makerArgsMap.put("name", new MakerArgs(String.class, ""));
            makerArgsMap.put("start", new MakerArgs(Number.class, "The smallest number returned."));
            makerArgsMap.put("end", new MakerArgs(Number.class, "The largest number returned."));
            makerArgsMap.put("random", new MakerArgs(Boolean.class, ""));
        }

        public String getType() {
            return "NumberRange";
        }

        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(makerArgsMap, args)) {
                return new NumberRangeMaker(name,
                        MakerArgs.toLong(args.get("start")),
                        MakerArgs.toLong(args.get("end")),
                        MakerArgs.toBoolean(args.getOrDefault("random", true)));
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
            makerArgsMap.put("name", new MakerArgs(String.class, ""));
            makerArgsMap.put("format", new MakerArgs(String.class, ""));
        }

        public String getType() {
            return "Date";
        }

        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(makerArgsMap, args)) {
                return new DateMaker(name, MakerArgs.toString(args.get("format")));
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
            makerArgsMap.put("name", new MakerArgs(String.class, ""));
        }

        public String getType() {
            return "UUID";
        }

        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            return new UUIDMaker(name);
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
            makerArgsMap.put("name", new MakerArgs(String.class, ""));
        }

        public String getType() {
            return "IP";
        }

        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            return new IPMaker(name);
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
            makerArgsMap.put("name", new MakerArgs(String.class, ""));
            makerArgsMap.put("start", new MakerArgs(String.class, "The smallest number returned."));
            makerArgsMap.put("end", new MakerArgs(String.class, "The largest number returned."));
            makerArgsMap.put("deviation", new MakerArgs(Number.class, ""));
        }

        public String getType() {
            return "IPRange";
        }

        public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(makerArgsMap, args)) {
                return new IPRangeMaker(name,
                        MakerArgs.toString(args.get("start")),
                        MakerArgs.toString(args.get("end")),
                        MakerArgs.toLong(args.get("deviation")));
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
            senderArgsMap.put("name", new SenderArgs(String.class, ""));
            senderArgsMap.put("ip", new SenderArgs(String.class, ""));
            senderArgsMap.put("port", new SenderArgs(Integer.class, ""));
            senderArgsMap.put("facility", new SenderArgs(Integer.class, ""));
            senderArgsMap.put("severity", new SenderArgs(Integer.class, ""));
            senderArgsMap.put("messageFormat", new SenderArgs(String.class, ""));
            senderArgsMap.put("hosts", new SenderArgs(ArrayList.class, ""));
            senderArgsMap.put("hostPrefix", new SenderArgs(String.class, ""));
        }

        public String getType() {
            return "Syslog";
        }

        @Override
        public Sender getSender(String name, Map<String, Object> args) throws ArgumentsNotValidException {
            if (this.checkArgs(senderArgsMap, args)) {
                return new SyslogSender(name,
                        SenderArgs.toString(args.get("ip")),
                        SenderArgs.toInt(args.get("port")),
                        SenderArgs.toInt(args.get("facility")),
                        SenderArgs.toInt(args.get("severity")),
                        SenderArgs.toString(args.get("messageFormat")),
                        SenderArgs.toList(args.get("hosts")),
                        SenderArgs.toString(args.get("hostPrefix")));
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
            senderArgsMap.put("name", new SenderArgs(String.class, ""));
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

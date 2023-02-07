package me.blueat.logmaker.plugins;

import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.Maker;
import me.blueat.logmaker.plugin.api.MakerArgs;
import me.blueat.logmaker.plugin.api.MakerPlugin;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class DefaultMakerPlugin extends Plugin {
    public DefaultMakerPlugin(PluginWrapper wrapper) {
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
            makerArgsMap.put("name", new MakerArgs(MakerArgs.Type.STRING, ""));
            makerArgsMap.put("regex", new MakerArgs(MakerArgs.Type.STRING, ""));
        }

        public String getType() {
            return "Regex";
        }

        @Override
        public Maker getMaker(String name, Map<String, Object> args) {
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
            makerArgsMap.put("name", new MakerArgs(MakerArgs.Type.STRING, ""));
            makerArgsMap.put("picker", new MakerArgs(MakerArgs.Type.LIST, ""));
        }

        public String getType() {
            return "Pick";
        }

        public Maker getMaker(String name, Map<String, Object> args) {
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
            makerArgsMap.put("name", new MakerArgs(MakerArgs.Type.STRING, ""));
            makerArgsMap.put("start", new MakerArgs(MakerArgs.Type.LONG, "The smallest number returned."));
            makerArgsMap.put("end", new MakerArgs(MakerArgs.Type.LONG, "The largest number returned."));
            makerArgsMap.put("random", new MakerArgs(MakerArgs.Type.BOOLEAN, ""));
        }

        public String getType() {
            return "NumberRange";
        }

        public Maker getMaker(String name, Map<String, Object> args) {
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
            makerArgsMap.put("name", new MakerArgs(MakerArgs.Type.STRING, ""));
            makerArgsMap.put("format", new MakerArgs(MakerArgs.Type.STRING, ""));
        }

        public String getType() {
            return "Date";
        }

        public Maker getMaker(String name, Map<String, Object> args) {
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
            makerArgsMap.put("name", new MakerArgs(MakerArgs.Type.STRING, ""));
        }

        public String getType() {
            return "UUID";
        }

        public Maker getMaker(String name, Map<String, Object> args) {
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
            makerArgsMap.put("name", new MakerArgs(MakerArgs.Type.STRING, ""));
        }

        public String getType() {
            return "IP";
        }

        public Maker getMaker(String name, Map<String, Object> args) {
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
            makerArgsMap.put("name", new MakerArgs(MakerArgs.Type.STRING, ""));
            makerArgsMap.put("start", new MakerArgs(MakerArgs.Type.STRING, "The smallest number returned."));
            makerArgsMap.put("end", new MakerArgs(MakerArgs.Type.STRING, "The largest number returned."));
            makerArgsMap.put("deviation", new MakerArgs(MakerArgs.Type.LONG, ""));
        }

        public String getType() {
            return "IPRange";
        }

        public Maker getMaker(String name, Map<String, Object> args) {
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
}

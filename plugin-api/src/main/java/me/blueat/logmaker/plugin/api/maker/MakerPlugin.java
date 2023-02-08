package me.blueat.logmaker.plugin.api.maker;

import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import org.pf4j.ExtensionPoint;

import java.util.Map;

@Slf4j
public abstract class MakerPlugin implements ExtensionPoint {
    public abstract String getType();
    public abstract Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException;
    public abstract Map<String, MakerArgs> getMakerArgsMap();

    /**
     *
     * @param makerArgsMap
     * @param args
     * @return
     */
    public boolean checkArgs(Map<String, MakerArgs> makerArgsMap, Map<String, Object> args) throws ArgumentsNotValidException {
        boolean check = true;

        log.info("{}", makerArgsMap);
        log.info("{}", args);

        if (!makerArgsMap.keySet().containsAll(args.keySet())) throw new ArgumentsNotValidException();

        args.keySet().forEach(key -> {
            Class argsClass = makerArgsMap.get(key).getType();
            if (!argsClass.isInstance(args.get(key))) {
                throw new ArgumentsNotValidException(key);
            }
        });

        return check;
    }
}

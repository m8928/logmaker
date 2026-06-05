package me.blueat.logmaker.plugin.api.maker;

import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import org.pf4j.ExtensionPoint;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class MakerPlugin implements ExtensionPoint {
    public abstract String getType();
    public abstract Maker<?> getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException;
    public abstract Map<String, MakerArgs> getMakerArgsMap();

    /**
     *
     * @param makerArgsMap
     * @param args
     * @return
     */
    public boolean checkArgs(Map<String, MakerArgs> makerArgsMap, Map<String, Object> args) throws ArgumentsNotValidException {
        log.debug("{}", makerArgsMap);
        log.debug("{}", args);

        if (!args.keySet().containsAll(makerArgsMap.entrySet().stream()
                .filter(e -> e.getValue().isRequired())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList()))) throw new ArgumentsNotValidException();

        args.keySet().forEach(key -> {
            if (makerArgsMap.containsKey(key)) {
                Class<?> argsClass = makerArgsMap.get(key).getType();
                if (args.get(key) == null || !argsClass.isInstance(args.get(key))) {
                    throw new ArgumentsNotValidException(key);
                }

                if (makerArgsMap.get(key).isRequired() && args.get(key) instanceof List && ((List<?>) args.get(key)).isEmpty()) {
                    throw new ArgumentsNotValidException(key);
                }
            }
        });

        return true;
    }
}

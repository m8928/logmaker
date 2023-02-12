package me.blueat.logmaker.plugin.api.sender;

import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import org.pf4j.ExtensionPoint;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class SenderPlugin  implements ExtensionPoint {
    public abstract String getType();
    public abstract Sender getSender(String name, Map<String, Object> args) throws ArgumentsNotValidException;
    public abstract Map<String, SenderArgs> getSenderArgsMap();

    /**
     *
     * @param senderArgsMap
     * @param args
     * @return
     */
    public boolean checkArgs(Map<String, SenderArgs> senderArgsMap, Map<String, Object> args) throws ArgumentsNotValidException {
        log.info("{}", senderArgsMap);
        log.info("{}", args);

        if (!args.keySet().containsAll(senderArgsMap.entrySet().stream()
                .filter(e -> e.getValue().isRequired())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList()))) throw new ArgumentsNotValidException();

        args.keySet().forEach(key -> {
            if (senderArgsMap.containsKey(key)) {
                Class argsClass = senderArgsMap.get(key).getType();
                if (args.get(key) == null || !argsClass.isInstance(args.get(key))) {
                    throw new ArgumentsNotValidException(key);
                }

                if (senderArgsMap.get(key).isRequired() && List.class.isInstance(args.get(key)) && ((List)args.get(key)).size() == 0) {
                    throw new ArgumentsNotValidException(key);
                }
            }
        });

        return true;
    }
}

package me.blueat.logmaker.plugin.api.sender;

import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import org.pf4j.ExtensionPoint;

import java.util.Map;

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
        boolean check = true;

        log.info("{}", senderArgsMap);
        log.info("{}", args);

        if (!senderArgsMap.keySet().containsAll(args.keySet())) throw new ArgumentsNotValidException();

        args.keySet().forEach(key -> {
            Class argsClass = senderArgsMap.get(key).getType();
            if (!argsClass.isInstance(args.get(key))) {
                throw new ArgumentsNotValidException(key);
            }
        });

        return check;
    }
}

package me.blueat.logmaker.plugin.api;

import lombok.extern.slf4j.Slf4j;
import org.pf4j.ExtensionPoint;

import java.util.Map;

@Slf4j
public abstract class MakerPlugin implements ExtensionPoint {
    public abstract String getType();
    public abstract Maker getMaker(String name, Map<String, Object> args);
    public abstract Map<String, MakerArgs> getMakerArgsMap();

    /**
     *
     * @param makerArgsMap
     * @param args
     * @return
     */
    public boolean checkArgs(Map<String, MakerArgs> makerArgsMap, Map<String, Object> args) {
        boolean check = true;
        log.debug("{}", makerArgsMap);
        log.debug("{}", args);
        return check;
    }
}

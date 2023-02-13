package me.blueat.logmaker.plugins.sender;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.sender.Sender;
import java.util.Map;

@Slf4j
@Data
public class DebugSender extends Sender<String> {
    private String name;

    public DebugSender(String name) {
        this.name = name;
    }

    @Override
    public String getSenderName() {
        return this.name;
    }

    @Override
    public void sendData(String data) {
        log.info("{}", data);
    }

    @Override
    public String getType() {
        return "Debug";
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

    }
}

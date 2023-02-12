package me.blueat.logmaker.plugins.sender;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.sender.Sender;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import java.io.StringWriter;
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
    public void sendData(Template vTemplate, Map<String, Object> data) {
        log.info("{}", generate(vTemplate, data));
    }

    @Override
    public String generate(Template vTemplate, Map<String, Object> data) {
        VelocityContext context = new VelocityContext();

        data.keySet().forEach(key -> {
            if (data.containsKey(key)) {
                context.put(key, data.get(key));
            }
        });

        StringWriter writer = new StringWriter();
        vTemplate.merge(context, writer);

        return writer.toString();
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

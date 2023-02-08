package me.blueat.logmaker.plugins.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.sender.Sender;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import java.io.StringWriter;
import java.util.Map;

@Slf4j
public class DebugSender implements Sender<String> {
    private String name;
    private final static ObjectMapper mapper = new ObjectMapper();

    public DebugSender(String name) {
        this.name = name;
    }

    @Override
    public String getSenderName() {
        return this.name;
    }

    @Override
    public void sendData(Template vTemplate, Map<String, String> data) {
        log.info("{}", mapper.convertValue(generate(vTemplate, data), Map.class));
    }

    @Override
    public String generate(Template vTemplate, Map<String, String> data) {
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
    public boolean isThread() {
        return false;
    }
}

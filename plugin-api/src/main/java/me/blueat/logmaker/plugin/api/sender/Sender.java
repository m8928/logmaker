package me.blueat.logmaker.plugin.api.sender;

import org.apache.velocity.Template;

import java.util.Map;

public interface Sender<T> {
    String getSenderName();
    void sendData(Template vTemplate, Map<String, String> data);
    T generate(Template vTemplate, Map<String, String> data);
    String getType();
    boolean isThread();
}

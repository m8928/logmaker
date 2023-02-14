package me.blueat.logmaker.plugin.api.sender;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Sender<T> {
    private AtomicInteger ref = new AtomicInteger(0);
    private AtomicLong count = new AtomicLong(0);
    private long regTime = LocalDateTime.now().atOffset(ZoneOffset.UTC).toEpochSecond();

    abstract public String getSenderName();
    abstract public void sendData(String data);
    public Map<String, Object> getArgs() {
        return new HashMap<>();
    }
    abstract public String getType();
    abstract public Thread getThread();
    abstract public boolean isThread();
    public int getRef() {
        return ref.get();
    }
    public void increaseRef() {
        ref.incrementAndGet();
    }
    public void decreaseRef() { ref.decrementAndGet(); }

    public long getCount() {
        return count.get();
    }
    public void increaseCount() {
        count.incrementAndGet();
    }
    public void decreaseCount() { count.decrementAndGet(); }

    abstract public void update(Map<String, Object> args);
    public long getRegTime() {
        return this.regTime;
    }
}

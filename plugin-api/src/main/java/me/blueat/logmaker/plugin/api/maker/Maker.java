package me.blueat.logmaker.plugin.api.maker;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Maker<T> {
    private AtomicInteger ref = new AtomicInteger(0);
    private long regTime = LocalDateTime.now().atOffset(ZoneOffset.UTC).toEpochSecond();
    @Getter
    private int queueSize = Integer.parseInt(System.getProperty("maker.queue.size", "100000"));

    abstract public T getData();
    abstract public String getMakerName();
    abstract public String getType();
    public Map<String, Object> getArgs() {
        return new HashMap<>();
    }
    abstract public long getSize();
    abstract public Thread getThread();
    abstract public boolean isThread();
    public int getRef() {
        return ref.get();
    }
    public void increaseRef() {
        ref.incrementAndGet();
    }
    public void decreaseRef() { ref.decrementAndGet(); }
    abstract public void update(Map<String, Object> args);
    public long getRegTime() {
        return this.regTime;
    }
}

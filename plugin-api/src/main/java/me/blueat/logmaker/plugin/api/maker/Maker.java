package me.blueat.logmaker.plugin.api.maker;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Maker<T> {
    private final AtomicInteger ref = new AtomicInteger(0);
    @Getter
    private final long regTime = LocalDateTime.now().atOffset(ZoneOffset.UTC).toEpochSecond();
    @Getter
    private final int queueSize = Integer.parseInt(System.getProperty("maker.queue.size", "20000"));

    public abstract T getData();

    public abstract String getMakerName();

    public abstract String getType();
    public Map<String, Object> getArgs() {
        return new HashMap<>();
    }

    public abstract long getSize();

    public abstract Thread getThread();

    public abstract boolean isThread();
    public int getRef() {
        return ref.get();
    }
    public void increaseRef() {
        ref.incrementAndGet();
    }
    public void decreaseRef() { ref.decrementAndGet(); }

    public abstract void update(Map<String, Object> args);
}

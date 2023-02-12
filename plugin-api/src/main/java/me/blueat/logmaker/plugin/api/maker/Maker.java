package me.blueat.logmaker.plugin.api.maker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Maker<T> {
    private AtomicInteger ref = new AtomicInteger(0);
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
}

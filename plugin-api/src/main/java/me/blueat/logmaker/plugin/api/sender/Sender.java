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
    private AtomicLong bytes = new AtomicLong(0);
    private volatile long lastSecondBytes = 0;
    private volatile long currentSecondBytes = 0;
    private volatile long lastTickSec = 0;
    private long limit = 0; // 0 = unlimited
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
    public void addBytes(long size) {
        bytes.addAndGet(size);
        long now = LocalDateTime.now().atOffset(ZoneOffset.UTC).toEpochSecond();
        if (now != lastTickSec) {
            lastSecondBytes = currentSecondBytes;
            currentSecondBytes = size;
            lastTickSec = now;
        } else {
            currentSecondBytes += size;
        }
    }
    public long getBytes() {
        return bytes.get();
    }
    public long getBytesPerSec() {
        long now = LocalDateTime.now().atOffset(ZoneOffset.UTC).toEpochSecond();
        if (now != lastTickSec) {
            return lastSecondBytes;
        }
        return currentSecondBytes;
    }
    public void decreaseCount() { count.decrementAndGet(); }

    public long getLimit() { return limit; }
    public void setLimit(long limit) { this.limit = limit; }
    public boolean isLimitReached() { return limit > 0 && count.get() >= limit; }

    abstract public void update(Map<String, Object> args);
    public void close() {
    }

    public long getRegTime() {
        return this.regTime;
    }
}

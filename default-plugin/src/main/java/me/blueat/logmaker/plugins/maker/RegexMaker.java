package me.blueat.logmaker.plugins.maker;

import com.github.curiousoddman.rgxgen.RgxGen;
import lombok.Data;
import lombok.EqualsAndHashCode;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerArgs;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Data
public class RegexMaker extends Maker<String> implements Runnable {
    private String makerName;
    private String type;
    private String regex;
    private ArrayBlockingQueue<String> queue;
    private Map<String, Object> args;
    private Thread thread;
    private Lock updateLock;
    private RgxGen rgxGen;
    private final AtomicLong configurationVersion = new AtomicLong();

    public RegexMaker(String makerName, String type, Map<String, Object> args) {
        thread = new Thread(this);
        thread.setName(String.format("THREAD_%s", makerName));
        this.updateLock = new ReentrantLock(true);
        this.queue = new ArrayBlockingQueue<>(getQueueSize());
        this.type = type;
        this.makerName = makerName;
        this.args = args;
        init();
    }

    public void init() {
        this.regex = MakerArgs.toString(args.get("regex"));
        this.rgxGen = RgxGen.parse(regex);
    }

    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            long version;
            String generated;
            updateLock.lock();
            try {
                version = configurationVersion.get();
                generated = getRegexRandomString();
            } finally {
                updateLock.unlock();
            }

            if (generated == null || version != configurationVersion.get()) {
                continue;
            }

            try {
                queue.put(generated);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public String getData() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private String getRegexRandomString() {
        CompletableFuture<String> withTimeout = CompletableFuture.supplyAsync(() -> rgxGen.generate());
        try {
            return withTimeout.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            withTimeout.cancel(true);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            withTimeout.cancel(true);
            return null;
        }
    }

    @Override
    public String getMakerName() {
        return makerName;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public long getSize() {
        return getQueueSize() - queue.remainingCapacity();
    }

    @Override
    public boolean isThread() {
        return true;
    }

    @Override
    public Map<String, Object> getArgs() {
        return this.args;
    }

    @Override
    public void update(Map<String, Object> args) {
        updateLock.lock();
        try {
            this.getThread().interrupt();
            this.args = args;
            configurationVersion.incrementAndGet();
            init();
            this.queue.clear();
            this.thread = new Thread(this);
            this.thread.start();
        } finally {
            updateLock.unlock();
        }
    }
}

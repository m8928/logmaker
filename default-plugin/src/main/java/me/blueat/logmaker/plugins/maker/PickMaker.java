package me.blueat.logmaker.plugins.maker;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerArgs;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Data
public class PickMaker extends Maker<String> implements Runnable {
    private String makerName;
    private String type;
    private List<String> picker;
    private ArrayBlockingQueue<String> queue;
    private Map<String, Object> args;
    private Thread thread;
    private Lock updateLock;
    private final AtomicLong configurationVersion = new AtomicLong();

    public PickMaker(String makerName, String type, Map<String, Object> args) {
        thread = new Thread(this);
        thread.setName(String.format("THREAD_%s", makerName));
        this.updateLock = new ReentrantLock(true);
        this.makerName = makerName;
        this.type = type;
        this.args = args;
        queue = new ArrayBlockingQueue<>(getQueueSize());
        init();
    }

    public void init() {
        this.picker = MakerArgs.toList(args.get("picker"));
    }

    @Override
    @SuppressWarnings("java:S2245")
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            updateLock.lock();
            String pick;
            long version;
            try {
                version = configurationVersion.get();
                pick = picker.get(ThreadLocalRandom.current().nextInt(picker.size()));
            }
            finally {
                updateLock.unlock();
            }

            if (version != configurationVersion.get()) {
                continue;
            }

            try {
                queue.put(pick);
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
            this.args = args;
            configurationVersion.incrementAndGet();
            init();
            this.queue.clear();
        } finally {
            updateLock.unlock();
        }
    }

}

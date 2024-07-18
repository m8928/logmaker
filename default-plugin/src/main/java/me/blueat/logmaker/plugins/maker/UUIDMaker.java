package me.blueat.logmaker.plugins.maker;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.blueat.logmaker.plugin.api.exception.MakerTimeoutException;
import me.blueat.logmaker.plugin.api.maker.Maker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Data
public class UUIDMaker extends Maker<String> implements Runnable {
    private String makerName;
    private String type;
    private ArrayBlockingQueue<String> queue;
    private Thread thread;
    private Lock updateLock;

    public UUIDMaker(String makerName, String type) {
        thread = new Thread(this);
        thread.setName(String.format("THREAD_%s", makerName));
        this.updateLock = new ReentrantLock(true);
        this.queue = new ArrayBlockingQueue<>(getQueueSize());
        this.type = type;
        this.makerName = makerName;
    }

    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            try {
                queue.put(UUID.randomUUID().toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public String getData() {
        try {
            return queue.poll(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new MakerTimeoutException();
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
    public void update(Map<String, Object> args) {
        updateLock.lock();
        try {
            // NOTHING
        } finally {
            updateLock.unlock();
        }
    }
}

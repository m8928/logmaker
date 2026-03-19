package me.blueat.logmaker.plugins.maker;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerArgs;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Data
public class NumberRangeMaker extends Maker<Long> implements Runnable {
    private String makerName;
    private String type;
    private long start;
    private long end;
    private boolean random;
    private ArrayBlockingQueue<Long> queue;
    private AtomicLong atomicLong;
    private Map<String, Object> args;
    private Thread thread;
    private Lock updateLock;

    public NumberRangeMaker(String makerName, String type, Map<String, Object> args) {
        thread = new Thread(this);
        thread.setName(String.format("THREAD_%s", makerName));
        this.updateLock = new ReentrantLock(true);
        this.makerName = makerName;
        this.type = type;
        this.args = args;
        this.queue = new ArrayBlockingQueue<>(getQueueSize());
        init();
    }

    public void init() {
        this.start = MakerArgs.toLong(args.get("start"));
        this.end = MakerArgs.toLong(args.get("end"));
        this.random = MakerArgs.toBoolean(args.getOrDefault("random", true));

        if (!random) {
            atomicLong = new AtomicLong(start);
        }
    }

    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            updateLock.lock();
            long number;
            try {
                if (random) {
                    number = (long) ((Math.random() * (end - start + 1)) + start);
                }
                else {
                    long value = atomicLong.getAndIncrement();

                    if (value > end) {
                        atomicLong.set(start);
                        number = start;
                    }
                    else {
                        number = value;
                    }
                }
            }
            finally {
                updateLock.unlock();
            }

            try {
                queue.put(number);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public Long getData() {
        try {
            return queue.take().longValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0L;
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
            init();
            this.getQueue().clear();
            this.thread = new Thread(this);
            this.thread.start();
        } finally {
            updateLock.unlock();
        }
    }
}

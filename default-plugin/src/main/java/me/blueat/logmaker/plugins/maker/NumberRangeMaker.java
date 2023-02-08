package me.blueat.logmaker.plugins.maker;

import me.blueat.logmaker.plugin.api.maker.Maker;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class NumberRangeMaker extends Thread implements Maker<Long> {
    private String makerName;
    private String type;
    private long start;
    private long end;
    private boolean random;
    private ArrayBlockingQueue<Long> queue;
    private AtomicLong atomicLong;

    public NumberRangeMaker(String makerName, long start, long end, boolean random) {
        super.setName(makerName);
        this.makerName = makerName;
        this.type = this.getClass().getName();
        this.queue = new ArrayBlockingQueue<>(1000000);
        this.start = start;
        this.end = end;
        this.random = random;

        if (!random) {
            atomicLong = new AtomicLong(start);
        }
    }

    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            try {
                if (random) {
                    queue.put((long) ((Math.random() * (end - start)) + start));
                }
                else {
                    long value = atomicLong.getAndIncrement();

                    if (value > end) {
                        atomicLong.set(start);
                        queue.put(start);
                    }
                    else {
                        queue.put(value);
                    }
                }
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
        return 1000000 - queue.remainingCapacity();
    }

    @Override
    public boolean isThread() {
        return true;
    }
}

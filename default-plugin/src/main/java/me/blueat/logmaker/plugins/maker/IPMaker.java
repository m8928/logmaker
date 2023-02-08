package me.blueat.logmaker.plugins.maker;

import me.blueat.logmaker.plugin.api.maker.Maker;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

public class IPMaker extends Thread implements Maker<String> {
    private String makerName;
    private String type;
    private ArrayBlockingQueue<String> queue;

    public IPMaker(String makerName) {
        super.setName(makerName);
        this.queue = new ArrayBlockingQueue<>(1000000);
        this.type = this.getClass().getName();
        this.makerName = makerName;
    }

    @Override
    public void run() {
        Random r = new Random();
        while(!Thread.currentThread().isInterrupted()) {
            try {
                queue.put(r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256));
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

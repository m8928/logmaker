package me.blueat.logmaker.plugins.maker;

import me.blueat.logmaker.plugin.api.maker.Maker;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class PickMaker extends Thread implements Maker<String> {
    private String makerName;
    private String type;
    private List<String> picker;
    private ArrayBlockingQueue<String> queue;

    public PickMaker(String makerName, List<String> picker) {
        super.setName(makerName);
        this.makerName = makerName;
        this.type = this.getClass().getName();
        this.picker = picker;
        queue = new ArrayBlockingQueue<>(1000000);
    }

    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            try {
                queue.put(picker.get((int) ((Math.random() * ((picker.size()) - 0)) + 0)));
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

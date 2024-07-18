package me.blueat.logmaker.plugins.maker;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.blueat.logmaker.plugin.api.exception.MakerTimeoutException;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerArgs;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Data
public class IPRangeMaker extends Maker<String> implements Runnable {
    private final String makerName;
    private final String type;

    private long startIpLong;
    private long endIpLong;
    private long avg;
    private long deviation;

    private final ArrayBlockingQueue<String> queue;
    private Map<String, Object> args;

    private Thread thread;
    private Lock updateLock;

    public IPRangeMaker(String makerName, String type, Map<String, Object> args) {
        thread = new Thread(this);
        thread.setName(String.format("THREAD_%s", makerName));
        this.updateLock = new ReentrantLock(true);
        this.makerName = makerName;
        this.args = args;
        this.type = type;
        this.queue = new ArrayBlockingQueue<>(getQueueSize());
        init();
    }

    public void init() {
        this.startIpLong = convertIP2Long(MakerArgs.toString(args.get("start")));
        this.endIpLong = convertIP2Long(MakerArgs.toString(args.get("end")));
        this.deviation = MakerArgs.toLong(args.getOrDefault("deviation", 0));
        this.avg = (endIpLong - startIpLong) / 2;

        if (deviation >= avg) {
            this.deviation = avg/2;
        }
    }

    @Override
    public void run() {
        Random r = new Random();

        while(!Thread.currentThread().isInterrupted()) {
            updateLock.lock();
            int ip;
            try {
                do {
                    double val = r.nextGaussian() * deviation + avg;
                    ip = (int) Math.round(val);
                } while (ip < 0 || (ip + startIpLong) > endIpLong);
            }
            finally {
                updateLock.unlock();
            }

            try {
                queue.put(convertLong2IP(ip + startIpLong));
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

    private long convertIP2Long(String ip) {
        String[] ipArray = ip.split("\\.");
        long result = 0;
        for (int index = 0; index < ipArray.length; index++) {
            result += Integer.parseInt(ipArray[index]) * Math.pow(256, 3 - index);
        }
        return result;
    }

    public String convertLong2IP(long ip) {
        StringBuilder result = new StringBuilder(15);
        for (int i = 0; i < 4; i++) {
            result.insert(0, Long.toString(ip & 0xff));
            if (i < 3) {
                result.insert(0, '.');
            }
            ip = ip >> 8;
        }
        return result.toString();
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
            init();
            this.getQueue().clear();
            // NOTHING
        } finally {
            updateLock.unlock();
        }
    }
}

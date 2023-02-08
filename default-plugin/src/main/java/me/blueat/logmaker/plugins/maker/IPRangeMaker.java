package me.blueat.logmaker.plugins.maker;

import me.blueat.logmaker.plugin.api.maker.Maker;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

public class IPRangeMaker extends Thread implements Maker<String> {
    private String makerName;
    private String type;

    private long startIpLong;
    private long endIpLong;
    private long avg;
    private long deviation;

    private ArrayBlockingQueue<String> queue;

    public IPRangeMaker(String makerName, String startIp, String endIp, long deviation) {
        super(makerName);
        this.makerName = makerName;
        this.type = this.getClass().getName();
        this.queue = new ArrayBlockingQueue<>(1000000);
        this.startIpLong = convertIP2Long(startIp);
        this.endIpLong = convertIP2Long(endIp);
        this.avg = (endIpLong - startIpLong) / 2;

        if (deviation >= avg) {
            this.deviation = avg/2;
        }
        else {
            this.deviation = deviation;
        }
    }

    @Override
    public void run() {
        Random r = new Random();

        while(!Thread.currentThread().isInterrupted()) {
            try {
                int ip;
                do {
                    double val = r.nextGaussian() * deviation + avg;
                    ip = (int) Math.round(val);
                } while (ip < 0 || (ip + startIpLong) > endIpLong);
                queue.put(convertLong2IP(ip + startIpLong));
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
        return 1000000 - queue.remainingCapacity();
    }

    @Override
    public boolean isThread() {
        return true;
    }
}

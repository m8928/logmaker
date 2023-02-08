package me.blueat.logmaker.plugins.maker;

import com.github.curiousoddman.rgxgen.RgxGen;
import me.blueat.logmaker.plugin.api.maker.Maker;

import java.util.concurrent.ArrayBlockingQueue;

public class RegexMaker extends Thread implements Maker<String> {
    private String makerName;
    private String type;
    private String regex;
    private ArrayBlockingQueue<String> queue;

    public RegexMaker(String makerName, String regex) {
        super.setName(makerName);
        this.queue = new ArrayBlockingQueue<>(1000000);
        this.type = this.getClass().getName();
        this.makerName = makerName;
        this.regex = regex;
    }

    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            try {
                queue.put(getRegexRandomString(regex));
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

    private static String getRegexRandomString(String regex) {
        RgxGen rgxGen = new RgxGen(regex);
        String s = rgxGen.generate();
        return s;
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

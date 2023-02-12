package me.blueat.logmaker.plugins.maker;

import com.github.curiousoddman.rgxgen.RgxGen;
import lombok.Data;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerArgs;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Data
public class RegexMaker extends Maker<String> implements Runnable {
    private String makerName;
    private String type;
    private String regex;
    private ArrayBlockingQueue<String> queue;
    private Map<String, Object> args;
    private Thread thread;
    private Lock updateLock;

    public RegexMaker(String makerName, String type, Map<String, Object> args) {
        thread = new Thread(this);
        thread.setName(String.format("THREAD_%s", makerName));
        this.updateLock = new ReentrantLock(true);
        this.queue = new ArrayBlockingQueue<>(1000000);
        this.type = type;
        this.makerName = makerName;
        this.args = args;
        init();
    }

    public void init() {
        this.regex = MakerArgs.toString(args.get("regex"));
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

    private String getRegexRandomString(String regex) {
        updateLock.lock();
        try {
            RgxGen rgxGen = new RgxGen(regex);
            String s = rgxGen.generate();
            return s;
        }
        finally {
            updateLock.unlock();
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
            this.queue.clear();
            // NOTHING
        } finally {
            updateLock.unlock();
        }
    }
}

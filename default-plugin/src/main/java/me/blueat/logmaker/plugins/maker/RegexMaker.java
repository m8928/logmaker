package me.blueat.logmaker.plugins.maker;

import com.github.curiousoddman.rgxgen.RgxGen;
import lombok.Data;
import lombok.EqualsAndHashCode;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerArgs;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Data
public class RegexMaker extends Maker<String> implements Runnable {
    private static final int REGEX_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final long GENERATION_FAILURE_BACKOFF_MS = 10L;
    private static final AtomicLong REGEX_THREAD_ID = new AtomicLong();
    private static final ExecutorService REGEX_EXECUTOR = new ThreadPoolExecutor(
            REGEX_POOL_SIZE,
            REGEX_POOL_SIZE,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(REGEX_POOL_SIZE * 2),
            RegexMaker::newRegexWorker,
            new ThreadPoolExecutor.AbortPolicy()
    );

    private String makerName;
    private String type;
    private String regex;
    private ArrayBlockingQueue<String> queue;
    private Map<String, Object> args;
    private Thread thread;
    private Lock updateLock;
    private RgxGen rgxGen;
    private final AtomicLong configurationVersion = new AtomicLong();

    public RegexMaker(String makerName, String type, Map<String, Object> args) {
        this.type = type;
        this.makerName = makerName;
        this.args = args;
        this.thread = new Thread(this, String.format("THREAD_%s", makerName));
        this.updateLock = new ReentrantLock(true);
        this.queue = new ArrayBlockingQueue<>(getQueueSize());
        init();
    }

    private static Thread newRegexWorker(Runnable task) {
        Thread thread = new Thread(task, "THREAD_regex-generator-" + REGEX_THREAD_ID.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    public void init() {
        this.regex = MakerArgs.toString(args.get("regex"));
        this.rgxGen = RgxGen.parse(regex);
    }

    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            long version;
            String generated;
            updateLock.lock();
            try {
                version = configurationVersion.get();
                generated = getRegexRandomString();
            } finally {
                updateLock.unlock();
            }

            if (version != configurationVersion.get()) {
                continue;
            }

            if (generated == null) {
                pauseAfterGenerationFailure();
                continue;
            }

            try {
                queue.put(generated);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void pauseAfterGenerationFailure() {
        try {
            Thread.sleep(GENERATION_FAILURE_BACKOFF_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    private String getRegexRandomString() {
        Future<String> generated;
        try {
            generated = REGEX_EXECUTOR.submit(() -> rgxGen.generate());
        } catch (RejectedExecutionException e) {
            return null;
        }

        try {
            return generated.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            generated.cancel(true);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            generated.cancel(true);
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
            configurationVersion.incrementAndGet();
            init();
            this.queue.clear();
            this.thread = new Thread(this, String.format("THREAD_%s", makerName));
            this.thread.start();
        } finally {
            updateLock.unlock();
        }
    }
}

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
    private static final int REGEX_WORKERS_PER_MAKER = 1;
    private static final long GENERATION_FAILURE_BACKOFF_MS = 10L;

    private String makerName;
    private String type;
    private String regex;
    private ArrayBlockingQueue<String> queue;
    private Map<String, Object> args;
    private Thread thread;
    private Lock updateLock;
    private RgxGen rgxGen;
    private final AtomicLong configurationVersion = new AtomicLong();
    private final AtomicLong regexThreadId = new AtomicLong();
    private ExecutorService regexExecutor;
    private volatile boolean closed;

    public RegexMaker(String makerName, String type, Map<String, Object> args) {
        this.type = type;
        this.makerName = makerName;
        this.args = args;
        this.thread = new Thread(this, String.format("THREAD_%s", makerName));
        this.updateLock = new ReentrantLock(true);
        this.queue = new ArrayBlockingQueue<>(getQueueSize());
        this.regexExecutor = createRegexExecutor();
        init();
    }

    private Thread newRegexWorker(Runnable task) {
        Thread thread = new Thread(task,
                String.format("THREAD_%s-regex-generator-%d", makerName, regexThreadId.incrementAndGet()));
        thread.setDaemon(true);
        return thread;
    }

    private ThreadPoolExecutor createRegexExecutor() {
        return new ThreadPoolExecutor(
                0,
                REGEX_WORKERS_PER_MAKER,
                30L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                this::newRegexWorker,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void init() {
        this.regex = MakerArgs.toString(args.get("regex"));
        this.rgxGen = RgxGen.parse(regex);
    }

    @Override
    public void run() {
        while(!closed && !Thread.currentThread().isInterrupted()) {
            long version;
            RgxGen generator;
            ExecutorService executor;
            updateLock.lock();
            try {
                version = configurationVersion.get();
                generator = rgxGen;
                executor = regexExecutor;
            } finally {
                updateLock.unlock();
            }

            String generated = getRegexRandomString(generator, executor);
            if (version == configurationVersion.get()) {
                if (generated == null) {
                    pauseAfterGenerationFailure();
                } else {
                    enqueueGenerated(generated);
                }
            }
        }
    }

    private void enqueueGenerated(String generated) {
        try {
            queue.put(generated);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    private String getRegexRandomString(RgxGen generator, ExecutorService executor) {
        Future<String> generated;
        try {
            generated = executor.submit(() -> generator.generate());
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
            shutdownRegexExecutor();
            this.args = args;
            configurationVersion.incrementAndGet();
            this.regexExecutor = createRegexExecutor();
            this.closed = false;
            init();
            this.queue.clear();
            this.thread = new Thread(this, String.format("THREAD_%s", makerName));
            this.thread.start();
        } finally {
            updateLock.unlock();
        }
    }

    @Override
    public void close() {
        updateLock.lock();
        try {
            this.closed = true;
            Thread currentThread = this.getThread();
            if (currentThread != null) {
                currentThread.interrupt();
            }
            shutdownRegexExecutor();
        } finally {
            updateLock.unlock();
        }
    }

    private void shutdownRegexExecutor() {
        if (regexExecutor != null) {
            regexExecutor.shutdownNow();
        }
    }
}

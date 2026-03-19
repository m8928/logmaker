package me.blueat.logmaker.plugins.maker;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that verify getData() never returns null when the thread is interrupted.
 * After H-03 fix, InterruptedException catch blocks return "" instead of null.
 */
class MakerInterruptTest {

    @Test
    void ipMaker_getData_returnsNotNull_whenInterrupted() throws InterruptedException {
        IPMaker maker = new IPMaker("testIP", "ip");
        // Do NOT start the thread - queue is empty, take() will block then be interrupted
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread caller = new Thread(() -> {
            result.set(maker.getData());
            latch.countDown();
        });
        caller.start();
        // Give caller time to block on queue.take()
        Thread.sleep(50);
        caller.interrupt();
        latch.await();

        assertNotNull(result.get(), "getData() must not return null on interrupt");
    }

    @Test
    void uuidMaker_getData_returnsNotNull_whenInterrupted() throws InterruptedException {
        UUIDMaker maker = new UUIDMaker("testUUID", "uuid");
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread caller = new Thread(() -> {
            result.set(maker.getData());
            latch.countDown();
        });
        caller.start();
        Thread.sleep(50);
        caller.interrupt();
        latch.await();

        assertNotNull(result.get(), "getData() must not return null on interrupt");
    }

    @Test
    void pickMaker_getData_returnsNotNull_whenInterrupted() throws InterruptedException {
        Map<String, Object> args = new HashMap<>();
        args.put("picker", Arrays.asList("a", "b", "c"));
        PickMaker maker = new PickMaker("testPick", "pick", args);
        // Do NOT start thread

        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread caller = new Thread(() -> {
            result.set(maker.getData());
            latch.countDown();
        });
        caller.start();
        Thread.sleep(50);
        caller.interrupt();
        latch.await();

        assertNotNull(result.get(), "getData() must not return null on interrupt");
    }

    @Test
    void numberRangeMaker_getData_returnsNotNull_whenInterrupted() throws InterruptedException {
        Map<String, Object> args = new HashMap<>();
        args.put("start", 1L);
        args.put("end", 100L);
        NumberRangeMaker maker = new NumberRangeMaker("testNum", "number", args);
        // Do NOT start thread

        AtomicReference<Long> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread caller = new Thread(() -> {
            result.set(maker.getData());
            latch.countDown();
        });
        caller.start();
        Thread.sleep(50);
        caller.interrupt();
        latch.await();

        assertNotNull(result.get(), "getData() must not return null on interrupt");
    }

    @Test
    void ipRangeMaker_getData_returnsNotNull_whenInterrupted() throws InterruptedException {
        Map<String, Object> args = new HashMap<>();
        args.put("start", "10.0.0.1");
        args.put("end", "10.0.0.255");
        IPRangeMaker maker = new IPRangeMaker("testIPRange", "iprange", args);
        // Do NOT start thread

        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread caller = new Thread(() -> {
            result.set(maker.getData());
            latch.countDown();
        });
        caller.start();
        Thread.sleep(50);
        caller.interrupt();
        latch.await();

        assertNotNull(result.get(), "getData() must not return null on interrupt");
    }

    @Test
    void regexMaker_getData_returnsNotNull_whenInterrupted() throws InterruptedException {
        Map<String, Object> args = new HashMap<>();
        args.put("regex", "[a-z]{5}");
        RegexMaker maker = new RegexMaker("testRegex", "regex", args);
        // Do NOT start thread

        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread caller = new Thread(() -> {
            result.set(maker.getData());
            latch.countDown();
        });
        caller.start();
        Thread.sleep(50);
        caller.interrupt();
        latch.await();

        assertNotNull(result.get(), "getData() must not return null on interrupt");
    }
}

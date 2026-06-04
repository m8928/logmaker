package me.blueat.logmaker.plugins.maker;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that verify getData() never returns null when the thread is interrupted.
 * After H-03 fix, InterruptedException catch blocks return "" instead of null.
 */
class MakerInterruptTest {

    @Test
    void ipMaker_getData_returnsNotNull_whenInterrupted() throws InterruptedException {
        IPMaker maker = new IPMaker("testIP", "ip");
        assertInterruptedGetDataReturnsNotNull(maker::getData);
    }

    @Test
    void uuidMaker_getData_returnsNotNull_whenInterrupted() throws InterruptedException {
        UUIDMaker maker = new UUIDMaker("testUUID", "uuid");
        assertInterruptedGetDataReturnsNotNull(maker::getData);
    }

    @Test
    void pickMaker_getData_returnsNotNull_whenInterrupted() throws InterruptedException {
        Map<String, Object> args = new HashMap<>();
        args.put("picker", Arrays.asList("a", "b", "c"));
        PickMaker maker = new PickMaker("testPick", "pick", args);
        assertInterruptedGetDataReturnsNotNull(maker::getData);
    }

    @Test
    void numberRangeMaker_getData_returnsNotNull_whenInterrupted() throws InterruptedException {
        Map<String, Object> args = new HashMap<>();
        args.put("start", 1L);
        args.put("end", 100L);
        NumberRangeMaker maker = new NumberRangeMaker("testNum", "number", args);
        assertInterruptedGetDataReturnsNotNull(maker::getData);
    }

    @Test
    void ipRangeMaker_getData_returnsNotNull_whenInterrupted() throws InterruptedException {
        Map<String, Object> args = new HashMap<>();
        args.put("start", "10.0.0.1");
        args.put("end", "10.0.0.255");
        IPRangeMaker maker = new IPRangeMaker("testIPRange", "iprange", args);
        assertInterruptedGetDataReturnsNotNull(maker::getData);
    }

    @Test
    void regexMaker_getData_returnsNotNull_whenInterrupted() throws InterruptedException {
        Map<String, Object> args = new HashMap<>();
        args.put("regex", "[a-z]{5}");
        RegexMaker maker = new RegexMaker("testRegex", "regex", args);
        assertInterruptedGetDataReturnsNotNull(maker::getData);
    }

    private <T> void assertInterruptedGetDataReturnsNotNull(Supplier<T> getData) throws InterruptedException {
        AtomicReference<T> result = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        Thread caller = new Thread(() -> {
            started.countDown();
            result.set(getData.get());
            finished.countDown();
        });
        caller.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));
        caller.interrupt();
        assertTrue(finished.await(1, TimeUnit.SECONDS));

        assertNotNull(result.get(), "getData() must not return null on interrupt");
    }
}

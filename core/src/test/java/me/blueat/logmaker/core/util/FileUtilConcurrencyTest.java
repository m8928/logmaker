package me.blueat.logmaker.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FileUtilConcurrencyTest {

    @TempDir
    Path tempDir;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testConcurrentSaveToFile_noCorruption() throws Exception {
        // Given
        Path filePath = tempDir.resolve("concurrent_test.json");
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        // When: launch 10 threads simultaneously writing different data to the same file
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    TestPayload payload = new TestPayload("thread-" + index, index * 100);
                    FileUtil.saveToFile(payload, filePath.toString());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startLatch.countDown(); // release all threads simultaneously

        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Then: verify the file contains valid JSON (not corrupted)
        assertDoesNotThrow(() -> {
            String content = Files.readString(filePath);
            Object parsed = objectMapper.readValue(content, Object.class);
            assertNotNull(parsed);
        }, "File should contain valid JSON after concurrent writes");
    }

    private static class TestPayload {
        private String name;
        private int value;

        public TestPayload() {
        }

        public TestPayload(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }
}

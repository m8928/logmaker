package me.blueat.logmaker.core.log;

import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.sender.Sender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogThreadTest {

    @Mock
    private MakerService makerService;

    @Mock
    private SenderService senderService;

    @BeforeEach
    void setUp() {
        // Default: no makers/senders registered
        lenient().when(makerService.getMakerNames()).thenReturn(Set.of());
        lenient().when(senderService.getSenderNames()).thenReturn(Set.of());
    }

    private LogDto simpleLogDto() {
        LogDto dto = new LogDto();
        dto.setName("testLog");
        dto.setFormat("static log line");
        dto.setEps(1);
        return dto;
    }

    /**
     * ST uses <varName> syntax (angle brackets), not $varName.
     */
    private LogDto logDtoWithMaker(String makerName) {
        LogDto dto = new LogDto();
        dto.setName("testLog");
        dto.setFormat("<" + makerName + ">");
        dto.setEps(1);
        return dto;
    }

    private LogDto logDtoWithMakerAndSender(String makerName, String senderName) {
        LogDto dto = new LogDto();
        dto.setName("testLog");
        dto.setFormat("<" + makerName + ">");
        dto.setEps(1);
        dto.setSender(List.of(senderName));
        return dto;
    }

    @Test
    void testGenerate_withValidTemplate() {
        // Given: simple static format (no variable substitution)
        LogDto dto = simpleLogDto();
        LogThread thread = new LogThread(makerService, senderService, dto);

        // When
        String result = thread.generate(thread.getVTemplate(), Map.of());

        // Then
        assertNotNull(result);
        assertEquals("static log line", result);
    }

    @Test
    void testSecureVelocityTemplate_doesNotUseRuntimeSingletonBypass() {
        LogDto dto = simpleLogDto();
        dto.setFormat("$class.forName(\"java.lang.Runtime\")");
        LogThread thread = new LogThread(makerService, senderService, dto);

        String result = thread.generate(thread.getVTemplate(), Map.of("class", Class.class));

        assertNotEquals("class java.lang.Runtime", result.trim());
    }

    @Test
    void testNextTargetUnits_accumulatesLowRates() {
        LogDto dto = simpleLogDto();
        LogThread thread = new LogThread(makerService, senderService, dto);

        List<Long> targets = List.of(
                thread.nextTargetUnits(false, 10, 60),
                thread.nextTargetUnits(false, 10, 60),
                thread.nextTargetUnits(false, 10, 60),
                thread.nextTargetUnits(false, 10, 60),
                thread.nextTargetUnits(false, 10, 60),
                thread.nextTargetUnits(false, 10, 60)
        );

        assertEquals(List.of(0L, 0L, 0L, 0L, 0L, 1L), targets);
    }

    @Test
    void testGetCurrentEps_beforeStart_returnsZero() {
        // Given
        LogDto dto = simpleLogDto();
        LogThread thread = new LogThread(makerService, senderService, dto);

        // When: getLogDto() triggers getCurrentEps()
        LogDto logDto = thread.getLogDto();

        // Then: EPS is 0 before thread has started running
        assertEquals(0L, logDto.getCurrentEps());
    }

    @Test
    void testGetLogDto_returnsCorrectStatus() {
        // Given
        LogDto dto = simpleLogDto();
        LogThread thread = new LogThread(makerService, senderService, dto);

        // When
        LogDto result = thread.getLogDto();

        // Then: basic fields are present
        assertEquals("testLog", result.getName());
        assertEquals("static log line", result.getFormat());
        assertEquals(0L, result.getCount());
    }

    @Test
    void testInterrupt_stopsThread() throws InterruptedException {
        // Given: LogThread is now Runnable, wrap in a Thread to test interrupt behavior
        LogDto dto = simpleLogDto();
        LogThread logThread = new LogThread(makerService, senderService, dto);
        Thread thread = new Thread(logThread);
        thread.start();

        awaitLogThreadStart(logThread);
        assertTrue(thread.isAlive());

        // When
        thread.interrupt();
        thread.join(2000);

        // Then
        assertFalse(thread.isAlive());
    }

    private void awaitLogThreadStart(LogThread logThread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (logThread.getStart() == null && System.nanoTime() < deadline) {
            Thread.yield();
        }
        assertNotNull(logThread.getStart(), "LogThread did not start");
    }

    @Test
    void testInterruptBeforeRun_stopsThreadImmediately() throws InterruptedException {
        LogDto dto = simpleLogDto();
        LogThread logThread = new LogThread(makerService, senderService, dto);
        logThread.interrupt();

        Thread thread = new Thread(logThread);
        thread.start();
        thread.join(500);

        assertFalse(thread.isAlive());
    }

    @Test
    void interruptCancelsAttachedFuture() {
        LogDto dto = simpleLogDto();
        LogThread logThread = new LogThread(makerService, senderService, dto);
        @SuppressWarnings("unchecked")
        Future<Object> future = mock(Future.class);

        logThread.attachRunningTask(future);
        logThread.interrupt();

        verify(future).cancel(true);
    }

    @Test
    void interruptBeforeFutureAttachCancelsFutureWhenAttached() {
        LogDto dto = simpleLogDto();
        LogThread logThread = new LogThread(makerService, senderService, dto);
        @SuppressWarnings("unchecked")
        Future<Object> future = mock(Future.class);

        logThread.interrupt();
        logThread.attachRunningTask(future);

        verify(future).cancel(true);
    }

    @Test
    void stopRunningTaskCancelsFutureWithoutPermanentInterrupt() {
        LogDto dto = simpleLogDto();
        LogThread logThread = new LogThread(makerService, senderService, dto);
        @SuppressWarnings("unchecked")
        Future<Object> future = mock(Future.class);

        logThread.attachRunningTask(future);
        logThread.stopRunningTask();

        verify(future).cancel(true);
        @SuppressWarnings("unchecked")
        Future<Object> resumedFuture = mock(Future.class);
        logThread.attachRunningTask(resumedFuture);
        verify(resumedFuture, never()).cancel(true);
    }

    @Test
    void getLogDtoDoesNotWaitForBlockingSenderSend() throws Exception {
        BlockingSender sender = new BlockingSender("blockingSender");
        when(senderService.getSenderNames()).thenReturn(Set.of("blockingSender"));
        when(senderService.getSender("blockingSender")).thenReturn(Optional.of(Map.entry("plugin", sender)));

        LogDto dto = simpleLogDto();
        dto.setSender(List.of("blockingSender"));
        LogThread thread = new LogThread(makerService, senderService, dto);

        CompletableFuture<Void> sendTask = CompletableFuture.runAsync(() ->
                ReflectionTestUtils.invokeMethod(thread, "generateBatch", false, 0L, 0L, 1L)
        );
        assertTrue(sender.awaitSendStart(), "sender send did not start");

        assertTimeoutPreemptively(Duration.ofMillis(200), thread::getLogDto);

        sender.release();
        sendTask.get(1, TimeUnit.SECONDS);
    }

    @Test
    void getLogDtoToleratesMakerRemovedDuringSnapshot() {
        @SuppressWarnings("unchecked")
        Maker<Object> maker = mock(Maker.class);
        when(makerService.getMakerNames()).thenReturn(Set.of("myMaker"));
        when(senderService.getSenderNames()).thenReturn(Set.of());
        when(makerService.getMaker("myMaker")).thenReturn(Optional.of(Map.entry("plugin", maker)));

        LogThread thread = new LogThread(makerService, senderService, logDtoWithMaker("myMaker"));
        thread.releaseReferences();

        assertDoesNotThrow(thread::getLogDto);
    }

    @Test
    void getLogDtoUsesCachedSampleWithoutReadingMaker() {
        @SuppressWarnings("unchecked")
        Maker<Object> maker = mock(Maker.class);
        when(makerService.getMakerNames()).thenReturn(Set.of("myMaker"));
        when(senderService.getSenderNames()).thenReturn(Set.of());
        when(makerService.getMaker("myMaker")).thenReturn(Optional.of(Map.entry("plugin", maker)));

        LogThread thread = new LogThread(makerService, senderService, logDtoWithMaker("myMaker"));

        LogDto snapshot = assertTimeoutPreemptively(Duration.ofMillis(200), thread::getLogDto);

        assertNull(snapshot.getSample());
        verify(maker, never()).getData();
    }

    @Test
    void generateBatchStopsAfterMakerRestoresInterruptStatus() {
        @SuppressWarnings("unchecked")
        Maker<Object> maker = mock(Maker.class);
        when(maker.getData()).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return "";
        });
        when(makerService.getMakerNames()).thenReturn(Set.of("myMaker"));
        when(senderService.getSenderNames()).thenReturn(Set.of());
        when(makerService.getMaker("myMaker")).thenReturn(Optional.of(Map.entry("plugin", maker)));

        try {
            LogThread thread = new LogThread(makerService, senderService, logDtoWithMaker("myMaker"));

            ReflectionTestUtils.invokeMethod(thread, "generateBatch", false, 0L, 0L, 10L);

            verify(maker, times(1)).getData();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void testUpdateLogDto_success() {
        // Given: initial log with static format
        LogDto dto = simpleLogDto();
        LogThread thread = new LogThread(makerService, senderService, dto);

        // When: update with new static format
        LogDto updated = new LogDto();
        updated.setName("testLog");
        updated.setFormat("updated line");
        updated.setEps(2);
        boolean result = thread.updateLogDto(updated);

        // Then
        assertTrue(result);
        assertEquals("updated line", thread.getLogDto().getFormat());
    }

    @Test
    void setPausedIsVisibleOnSnapshotsAndSurvivesUpdates() {
        LogDto dto = simpleLogDto();
        LogThread thread = new LogThread(makerService, senderService, dto);
        thread.setPaused(true);

        LogDto updated = new LogDto();
        updated.setName("testLog");
        updated.setFormat("updated line");
        updated.setEps(2);

        assertTrue(thread.updateLogDto(updated));
        assertTrue(thread.getLogDto().isPaused());
        assertEquals("updated line", thread.getLogDto().getFormat());
    }

    @Test
    void testUpdateLogDto_failure_rollsBack() {
        // Given: initial log with maker reference (ST syntax: <makerName>)
        @SuppressWarnings("unchecked")
        Maker<Object> maker = mock(Maker.class);
        when(maker.getData()).thenReturn("maker-value");
        when(makerService.getMakerNames()).thenReturn(Set.of("myMaker"));
        when(senderService.getSenderNames()).thenReturn(Set.of());
        when(makerService.getMaker("myMaker")).thenReturn(Optional.of(Map.entry("plugin", maker)));

        LogDto initial = logDtoWithMaker("myMaker");
        LogThread thread = new LogThread(makerService, senderService, initial);
        ReflectionTestUtils.invokeMethod(thread, "generateBatch", false, 0L, 0L, 1L);

        // When: update with format referencing an unknown maker
        // <unknownMaker> is not in getMakerNames() → IllegalStateException → result = false
        LogDto badDto = new LogDto();
        badDto.setName("testLog");
        badDto.setFormat("<unknownMaker>");
        badDto.setEps(1);

        boolean result = thread.updateLogDto(badDto);

        // Then: returns false (update failed, rolled back to original)
        assertFalse(result);
        assertEquals("<myMaker>", thread.getLogDto().getFormat());
        assertEquals("maker-value", thread.getLogDto().getSample());
        verify(maker, times(2)).increaseRef();
        verify(maker).decreaseRef();
    }

    @Test
    void testUpdateLogDto_withMakerAndSender() {
        // Given: set up maker and sender mocks
        @SuppressWarnings("unchecked")
        Maker<Object> maker = mock(Maker.class);
        lenient().when(maker.getData()).thenReturn("192.168.1.1");

        @SuppressWarnings("unchecked")
        Sender<Object> sender = mock(Sender.class);

        when(makerService.getMakerNames()).thenReturn(Set.of("myMaker"));
        when(senderService.getSenderNames()).thenReturn(Set.of("mySender"));
        when(makerService.getMaker("myMaker")).thenReturn(Optional.of(Map.entry("plugin", maker)));
        when(senderService.getSender("mySender")).thenReturn(Optional.of(Map.entry("plugin", sender)));

        LogDto dto = logDtoWithMakerAndSender("myMaker", "mySender");
        LogThread thread = new LogThread(makerService, senderService, dto);

        // When: generate a log line
        String line = thread.generate(thread.getVTemplate(),
                Map.of("myMaker", "192.168.1.1"));

        // Then: template rendered with maker value
        assertNotNull(line);
        assertEquals("192.168.1.1", line);
    }

    private static class BlockingSender extends Sender<String> {
        private final String name;
        private final CountDownLatch sendStarted = new CountDownLatch(1);
        private final CountDownLatch releaseSend = new CountDownLatch(1);

        private BlockingSender(String name) {
            this.name = name;
        }

        @Override
        public String getSenderName() {
            return name;
        }

        @Override
        public void sendData(String data) {
            sendStarted.countDown();
            try {
                releaseSend.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public String getType() {
            return "blocking";
        }

        @Override
        public Thread getThread() {
            return null;
        }

        @Override
        public boolean isThread() {
            return false;
        }

        @Override
        public void update(Map<String, Object> args) {
            // Test sender has no mutable configuration.
        }

        private boolean awaitSendStart() throws InterruptedException {
            return sendStarted.await(1, TimeUnit.SECONDS);
        }

        private void release() {
            releaseSend.countDown();
        }
    }
}

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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

        // Allow thread to start
        Thread.sleep(100);
        assertTrue(thread.isAlive());

        // When
        thread.interrupt();
        thread.join(2000);

        // Then
        assertFalse(thread.isAlive());
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
        // getData() called by LogThread.init() via getSample during construction
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
}

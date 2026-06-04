package me.blueat.logmaker.core.scenario;

import me.blueat.logmaker.core.log.LogService;
import me.blueat.logmaker.core.log.LogThread;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.sender.Sender;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioThreadTest {

    private final TestMakerService makerService = new TestMakerService();
    private final TestSenderService senderService = new TestSenderService();
    private final TestLogService logService = new TestLogService();

    @Test
    void routesEachStepToItsOwnSenders() {
        LogThread loginLog = logThread("login", "login-event");
        LogThread logoutLog = logThread("logout", "logout-event");
        logService.add(loginLog);
        logService.add(logoutLog);

        CapturingSender scenarioSender = new CapturingSender("scenario-sender");
        CapturingSender loginSender = new CapturingSender("login-sender");
        CapturingSender logoutSender = new CapturingSender("logout-sender");
        senderService.add(scenarioSender);
        senderService.add(loginSender);
        senderService.add(logoutSender);

        ScenarioDto scenario = scenario(List.of(
                step("login", List.of("login-sender")),
                step("logout", List.of("logout-sender"))
        ));
        scenario.setSenders(List.of("scenario-sender"));

        new ScenarioThread(makerService, senderService, logService, scenario).run();

        assertEquals(List.of("login-event"), loginSender.getSentData());
        assertEquals(List.of("logout-event"), logoutSender.getSentData());
        assertEquals(List.of(), scenarioSender.getSentData());
    }

    @Test
    void ignoresScenarioSendersWhenStepHasNoSenders() {
        LogThread loginLog = logThread("login", "login-event");
        logService.add(loginLog);

        CapturingSender scenarioSender = new CapturingSender("scenario-sender");
        senderService.add(scenarioSender);

        ScenarioDto scenario = scenario(List.of(step("login", List.of())));
        scenario.setSenders(List.of("scenario-sender"));

        new ScenarioThread(makerService, senderService, logService, scenario).run();

        assertEquals(List.of(), scenarioSender.getSentData());
    }

    @Test
    void rendersSameLogForDifferentStepOverrides() {
        LogThread loginLog = logThread("login", "${value}");
        logService.add(loginLog);

        CapturingSender sender = new CapturingSender("scenario-sender");
        senderService.add(sender);

        ScenarioStepDto first = step("login", List.of("scenario-sender"));
        first.setOverrides(Map.of("value", "first"));
        ScenarioStepDto second = step("login", List.of("scenario-sender"));
        second.setOverrides(Map.of("value", "second"));
        ScenarioDto scenario = scenario(List.of(first, second));

        new ScenarioThread(makerService, senderService, logService, scenario).run();

        assertEquals(List.of("first", "second"), sender.getSentData());
    }

    @Test
    void rendersOverridesAndSharedVariablesThroughTemplateContext() {
        makerService.add(new FixedMaker("value", "maker-value"));
        makerService.add(new FixedMaker("sharedValue", "base-shared"));
        makerService.add(new FixedMaker("shared-maker", "shared-value"));
        LogThread loginLog = logThread("login", "<value>-<sharedValue>");
        logService.add(loginLog);

        CapturingSender sender = new CapturingSender("scenario-sender");
        senderService.add(sender);

        ScenarioStepDto step = step("login", List.of("scenario-sender"));
        step.setOverrides(Map.of("value", "step-value", "sharedValue", "step-shared"));
        ScenarioDto scenario = scenario(List.of(step));
        scenario.setSharedVariables(Map.of("sharedValue", "shared-maker"));

        new ScenarioThread(makerService, senderService, logService, scenario).run();

        assertEquals(List.of("step-value-shared-value"), sender.getSentData());
    }

    @Test
    void continuesScenarioWhenOneSenderFails() {
        LogThread loginLog = logThread("login", "login-event");
        logService.add(loginLog);

        ThrowingSender failingSender = new ThrowingSender("failing-sender");
        CapturingSender healthySender = new CapturingSender("healthy-sender");
        senderService.add(failingSender);
        senderService.add(healthySender);

        ScenarioDto scenario = scenario(List.of(
                step("login", List.of("failing-sender", "healthy-sender"))
        ));

        new ScenarioThread(makerService, senderService, logService, scenario).run();

        assertEquals(List.of("login-event"), healthySender.getSentData());
    }

    @Test
    void releasesSenderRefsWhenResolvingSendersFails() {
        LogThread loginLog = logThread("login", "login-event");
        logService.add(loginLog);

        CapturingSender retainedSender = new CapturingSender("retained-sender");
        senderService.add(retainedSender);
        senderService.failLookup("missing-sender");

        ScenarioDto scenario = scenario(List.of(
                step("login", List.of("retained-sender", "missing-sender"))
        ));

        ScenarioThread scenarioThread = new ScenarioThread(makerService, senderService, logService, scenario);

        assertThrows(IllegalStateException.class, scenarioThread::run);
        assertEquals(0, retainedSender.getRef());
        assertFalse(scenarioThread.getRunning().get());
    }

    @Test
    void incrementsLoopCounterInInfiniteScenarios() throws InterruptedException {
        LogThread loginLog = logThread("login", "login-event");
        logService.add(loginLog);

        ScenarioDto scenario = scenario(List.of(step("login", List.of())));
        scenario.setLoopCount(0);
        ScenarioThread scenarioThread = new ScenarioThread(makerService, senderService, logService, scenario);
        Thread worker = new Thread(scenarioThread);

        worker.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (scenarioThread.getCurrentLoop().get() < 2 && System.nanoTime() < deadline) {
            Thread.yield();
        }
        scenarioThread.interrupt();
        worker.join(2_000);

        assertTrue(scenarioThread.getCurrentLoop().get() > 1);
        assertFalse(worker.isAlive());
    }

    private ScenarioDto scenario(List<ScenarioStepDto> steps) {
        ScenarioDto scenario = new ScenarioDto();
        scenario.setName("test-scenario");
        scenario.setLoopCount(1);
        scenario.setIntervalMinMs(0);
        scenario.setIntervalMaxMs(0);
        scenario.setSteps(steps);
        return scenario;
    }

    private ScenarioStepDto step(String logName, List<String> senders) {
        ScenarioStepDto step = new ScenarioStepDto();
        step.setLogName(logName);
        step.setRepeat(1);
        step.setDelayMinMs(0);
        step.setDelayMaxMs(0);
        step.setSenders(senders);
        return step;
    }

    private LogThread logThread(String name, String format) {
        LogDto logDto = new LogDto();
        logDto.setName(name);
        logDto.setFormat(format);
        logDto.setSender(List.of());
        return new LogThread(makerService, senderService, logDto);
    }

    private Optional<Map.Entry<String, Sender<?>>> senderEntry(Sender<?> sender) {
        return Optional.of(new AbstractMap.SimpleEntry<>("test-plugin", sender));
    }

    private static class TestMakerService extends MakerService {
        private final Map<String, Maker<?>> makers = new HashMap<>();

        private TestMakerService() {
            super(null, null, null);
        }

        private void add(Maker<?> maker) {
            makers.put(maker.getMakerName(), maker);
        }

        @Override
        public Set<String> getMakerNames() {
            return new HashSet<>(makers.keySet());
        }

        @Override
        public Optional<Map.Entry<String, Maker<?>>> getMaker(String name) {
            Maker<?> maker = makers.get(name);
            return maker != null ? Optional.of(new AbstractMap.SimpleEntry<>("test-plugin", maker)) : Optional.empty();
        }
    }

    private class TestSenderService extends SenderService {
        private final Map<String, Sender<?>> senders = new HashMap<>();
        private final Set<String> failingLookups = new HashSet<>();

        private TestSenderService() {
            super(null, null, null);
        }

        private void add(Sender<?> sender) {
            senders.put(sender.getSenderName(), sender);
        }

        private void failLookup(String name) {
            failingLookups.add(name);
        }

        @Override
        public Set<String> getSenderNames() {
            return new HashSet<>(senders.keySet());
        }

        @Override
        public Optional<Map.Entry<String, Sender<?>>> getSender(String name) {
            if (failingLookups.contains(name)) {
                throw new IllegalStateException("sender lookup failed");
            }
            Sender<?> sender = senders.get(name);
            return sender != null ? senderEntry(sender) : Optional.empty();
        }
    }

    private static class FixedMaker extends Maker<String> {
        private final String name;
        private final String value;

        private FixedMaker(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getData() {
            return value;
        }

        @Override
        public String getMakerName() {
            return name;
        }

        @Override
        public String getType() {
            return "fixed";
        }

        @Override
        public long getSize() {
            return 0;
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
            // Fixed maker has no mutable configuration in tests.
        }
    }

    private static class TestLogService extends LogService {
        private final Map<String, LogThread> logs = new HashMap<>();

        private TestLogService() {
            super(null, null, null, null);
        }

        private void add(LogThread logThread) {
            logs.put(logThread.getLogDto().getName(), logThread);
        }

        @Override
        public LogThread getLog(String name) {
            return logs.get(name);
        }
    }

    private static class CapturingSender extends Sender<String> {
        private final String name;
        private final List<String> sentData = new ArrayList<>();

        private CapturingSender(String name) {
            this.name = name;
        }

        @Override
        public String getSenderName() {
            return name;
        }

        @Override
        public void sendData(String data) {
            sentData.add(data);
        }

        @Override
        public String getType() {
            return "Capturing";
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
            // Test sender has no mutable configuration to update.
        }

        private List<String> getSentData() {
            return sentData;
        }
    }

    private static class ThrowingSender extends CapturingSender {
        private ThrowingSender(String name) {
            super(name);
        }

        @Override
        public void sendData(String data) {
            throw new RuntimeException("send failed");
        }
    }
}

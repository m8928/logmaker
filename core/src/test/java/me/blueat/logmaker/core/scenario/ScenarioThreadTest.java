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

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void recompilesSameLogForDifferentStepOverrides() {
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
        private TestMakerService() {
            super(null, null, null);
        }

        @Override
        public Set<String> getMakerNames() {
            return Set.of();
        }

        @Override
        public Optional<Map.Entry<String, Maker<?>>> getMaker(String name) {
            return Optional.empty();
        }
    }

    private class TestSenderService extends SenderService {
        private final Map<String, Sender<?>> senders = new HashMap<>();

        private TestSenderService() {
            super(null, null, null);
        }

        private void add(Sender<?> sender) {
            senders.put(sender.getSenderName(), sender);
        }

        @Override
        public Set<String> getSenderNames() {
            return new HashSet<>(senders.keySet());
        }

        @Override
        public Optional<Map.Entry<String, Sender<?>>> getSender(String name) {
            Sender<?> sender = senders.get(name);
            return sender != null ? senderEntry(sender) : Optional.empty();
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

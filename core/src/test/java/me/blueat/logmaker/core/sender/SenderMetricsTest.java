package me.blueat.logmaker.core.sender;

import me.blueat.logmaker.plugin.api.sender.Sender;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SenderMetricsTest {
    @Test
    void bytesPerSecondReturnsZeroAfterIdlePeriod() {
        TestSender sender = new TestSender();

        sender.setNow(100);
        sender.addBytes(50);
        assertEquals(50, sender.getBytesPerSec());

        sender.setNow(102);
        assertEquals(0, sender.getBytesPerSec());

        sender.addBytes(10);
        assertEquals(10, sender.getBytesPerSec());

        sender.setNow(104);
        assertEquals(0, sender.getBytesPerSec());
    }

    private static class TestSender extends Sender<String> {
        private long now;

        private void setNow(long now) {
            this.now = now;
        }

        @Override
        protected long currentEpochSecond() {
            return now;
        }

        @Override
        public String getSenderName() {
            return "test";
        }

        @Override
        public void sendData(String data) {
            // Metrics tests do not send payloads.
        }

        @Override
        public String getType() {
            return "test";
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
            // Metrics tests do not mutate sender configuration.
        }
    }
}

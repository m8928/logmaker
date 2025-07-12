package me.blueat.logmaker.plugins.maker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class IPRangeMakerTest {

    private IPRangeMaker ipRangeMaker;
    // This is the corrected regex pattern for Java.
    private final Pattern ipPattern = Pattern.compile("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    @BeforeEach
    void setUp() {
        Map<String, Object> args = new HashMap<>();
        args.put("start", "192.168.0.1");
        args.put("end", "192.168.0.255");
        args.put("deviation", 50L);

        ipRangeMaker = new IPRangeMaker("test-ip-range", "ip-range", args);
        ipRangeMaker.getThread().start();
    }

    @AfterEach
    void tearDown() {
        // Stop the thread to ensure clean test runs
        if (ipRangeMaker != null && ipRangeMaker.getThread() != null) {
            ipRangeMaker.getThread().interrupt();
        }
    }

    @Test
    @DisplayName("IP 주소와 Long 변환 테스트")
    void testIPAddressConversion() {
        assertThat(ipRangeMaker.convertLong2IP(3232235521L)).isEqualTo("192.168.0.1");
        assertThat(ipRangeMaker.convertLong2IP(3232235775L)).isEqualTo("192.168.0.255");
        assertThat(ipRangeMaker.convertLong2IP(0L)).isEqualTo("0.0.0.0");
        assertThat(ipRangeMaker.convertLong2IP(4294967295L)).isEqualTo("255.255.255.255");
    }

    @Test
    @DisplayName("생성된 IP가 유효한 형식인지 테스트")
    void testGeneratedIPIsValidFormat() {
        String ip = ipRangeMaker.getData();
        assertThat(ip).isNotNull();
        assertThat(ipPattern.matcher(ip).matches()).isTrue();
    }

    @Test
    @DisplayName("생성된 IP가 지정된 범위 내에 있는지 테스트")
    void testGeneratedIPIsWithinRange() {
        for (int i = 0; i < 10; i++) {
            String ip = ipRangeMaker.getData();
            assertThat(ip).isNotNull();
            long ipAsLong = convertIP2Long(ip);
            assertThat(ipAsLong).isBetween(3232235521L, 3232235775L);
        }
    }

    @Test
    @DisplayName("업데이트 후에도 IP가 새 범위 내에서 생성되는지 테스트")
    void testGetDataAfterUpdate() throws InterruptedException {
        Map<String, Object> newArgs = new HashMap<>();
        newArgs.put("start", "10.0.0.1");
        newArgs.put("end", "10.0.0.10");
        newArgs.put("deviation", 2L);

        ipRangeMaker.update(newArgs);

        // Allow some time for the queue to be populated by the worker thread
        Thread.sleep(100);

        for (int i = 0; i < 5; i++) {
            String ip = ipRangeMaker.getData();
            assertThat(ip).isNotNull();
            long ipAsLong = convertIP2Long(ip);
            assertThat(ipAsLong).isBetween(167772161L, 167772170L);
        }
    }

    // Helper method to convert IP to long for assertion
    private long convertIP2Long(String ip) {
        String[] ipArray = ip.split("\\.");
        long result = 0;
        for (int index = 0; index < ipArray.length; index++) {
            result += Integer.parseInt(ipArray[index]) * Math.pow(256, 3 - index);
        }
        return result;
    }
}
package me.blueat.logmaker.plugins.maker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class IPRangeMakerTest {

    private IPRangeMaker ipRangeMaker;
    private static final String START_IP_1 = "192.168.0.1";
    private static final long START_IP_1_LONG = 3232235521L;
    private static final String END_IP_1 = "192.168.0.255";
    private static final long END_IP_1_LONG = 3232235775L;
    private static final String START_IP_2 = "10.0.0.1";
    private static final long START_IP_2_LONG = 167772161L;
    private static final String END_IP_2 = "10.0.0.10";
    private static final long END_IP_2_LONG = 167772170L;

    // This is the corrected regex pattern for Java.
    private final Pattern ipPattern = Pattern.compile("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    @BeforeEach
    void setUp() {
        Map<String, Object> args = new HashMap<>();
        args.put("start", START_IP_1);
        args.put("end", END_IP_1);
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
        assertDoesNotThrow(() -> {
            assertThat(ipRangeMaker.convertLong2IP(START_IP_1_LONG)).isEqualTo(START_IP_1);
            assertThat(ipRangeMaker.convertLong2IP(END_IP_1_LONG)).isEqualTo(END_IP_1);
            assertThat(ipRangeMaker.convertLong2IP(0L)).isEqualTo("0.0.0.0");
            assertThat(ipRangeMaker.convertLong2IP(4294967295L)).isEqualTo("255.255.255.255");
        });
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
            assertThat(ipAsLong).isBetween(START_IP_1_LONG, END_IP_1_LONG);
        }
    }

    @Test
    @DisplayName("업데이트 후에도 IP가 새 범위 내에서 생성되는지 테스트")
    void testGetDataAfterUpdate() {
        Map<String, Object> newArgs = new HashMap<>();
        newArgs.put("start", START_IP_2);
        newArgs.put("end", END_IP_2);
        newArgs.put("deviation", 2L);

        ipRangeMaker.update(newArgs);

        for (int i = 0; i < 5; i++) {
            String ip = ipRangeMaker.getData();
            assertThat(ip).isNotNull();
            long ipAsLong = convertIP2Long(ip);
            assertThat(ipAsLong).isBetween(START_IP_2_LONG, END_IP_2_LONG);
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

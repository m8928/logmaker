package me.blueat.logmaker.plugins.maker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RegexMakerTest {

    private RegexMaker regexMaker;

    @AfterEach
    void tearDown() {
        if (regexMaker != null) {
            regexMaker.close();
        }
    }

    @Test
    @DisplayName("단순 정규식으로 문자열을 정상적으로 생성하는지 테스트")
    void testSimpleRegexGeneration() {
        String regex = "[a-zA-Z0-9]{10}";
        Map<String, Object> args = new HashMap<>();
        args.put("regex", regex);

        regexMaker = new RegexMaker("test-simple-regex", "regex", args);
        regexMaker.getThread().start();

        String generated = regexMaker.getData();
        assertThat(generated).isNotNull().matches(regex);
    }

    @Test
    @DisplayName("IP 주소 정규식으로 문자열을 생성하는지 테스트")
    void testIPAddressRegexGeneration() {
        String ipRegex = "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
        Map<String, Object> args = new HashMap<>();
        args.put("regex", ipRegex);

        regexMaker = new RegexMaker("test-ip-regex", "regex", args);
        regexMaker.getThread().start();

        for (int i = 0; i < 5; i++) {
            String generated = regexMaker.getData();
            assertThat(generated).isNotNull().matches(ipRegex);
        }
    }

    @Test
    @DisplayName("업데이트 후 새로운 정규식으로 문자열을 생성하는지 테스트")
    void testGenerationAfterUpdate() throws InterruptedException {
        Map<String, Object> initialArgs = new HashMap<>();
        initialArgs.put("regex", "[0-9]{3}");
        regexMaker = new RegexMaker("test-update-regex", "regex", initialArgs);
        regexMaker.getThread().start();

        String newRegex = "[a-z]{5}";
        Map<String, Object> newArgs = new HashMap<>();
        newArgs.put("regex", newRegex);

        regexMaker.update(newArgs);

        Thread.sleep(100); // Allow queue to populate

        for (int i = 0; i < 5; i++) {
            String generated = regexMaker.getData();
            assertThat(generated).isNotNull().matches(newRegex);
        }
    }

    @Test
    @DisplayName("close 호출 시 maker thread를 종료하는지 테스트")
    void testCloseStopsMakerThread() throws InterruptedException {
        Map<String, Object> args = new HashMap<>();
        args.put("regex", "[a-z]{5}");
        regexMaker = new RegexMaker("test-close-regex", "regex", args);
        Thread worker = regexMaker.getThread();
        worker.start();

        regexMaker.close();
        worker.join(2_000);

        assertThat(worker.isAlive()).isFalse();
    }
}

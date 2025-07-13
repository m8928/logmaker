package me.blueat.logmaker.plugins.maker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NumberRangeMakerTest {

    private NumberRangeMaker numberRangeMaker;

    @AfterEach
    void tearDown() {
        if (numberRangeMaker != null && numberRangeMaker.getThread() != null) {
            numberRangeMaker.getThread().interrupt();
        }
    }

    @Test
    @DisplayName("랜덤 숫자가 지정된 범위 내에서 생성되는지 테스트")
    void testRandomNumberGeneration() {
        Map<String, Object> args = new HashMap<>();
        args.put("start", 100L);
        args.put("end", 200L);
        args.put("random", true);

        numberRangeMaker = new NumberRangeMaker("test-random-number", "number-range", args);
        numberRangeMaker.getThread().start();

        for (int i = 0; i < 20; i++) {
            Long number = numberRangeMaker.getData();
            assertThat(number).isNotNull().isBetween(100L, 200L);
        }
    }

    @Test
    @DisplayName("순차 숫자가 지정된 범위 내에서 순서대로 생성되는지 테스트")
    void testSequentialNumberGeneration() {
        Map<String, Object> args = new HashMap<>();
        args.put("start", 1L);
        args.put("end", 5L);
        args.put("random", false);

        numberRangeMaker = new NumberRangeMaker("test-sequential-number", "number-range", args);
        numberRangeMaker.getThread().start();

        for (long i = 1; i <= 5; i++) {
            Long number = numberRangeMaker.getData();
            assertThat(number).isNotNull().isEqualTo(i);
        }

        // 범위가 초과되면 다시 처음부터 시작하는지 확인
        Long number = numberRangeMaker.getData();
        assertThat(number).isNotNull().isEqualTo(1L);
    }

    @Test
    @DisplayName("업데이트 후 새 범위의 숫자를 생성하는지 테스트")
    void testGetDataAfterUpdate() throws InterruptedException {
        Map<String, Object> initialArgs = new HashMap<>();
        initialArgs.put("start", 1L);
        initialArgs.put("end", 5L);
        initialArgs.put("random", true);
        numberRangeMaker = new NumberRangeMaker("test-update-number", "number-range", initialArgs);
        numberRangeMaker.getThread().start();

        Map<String, Object> newArgs = new HashMap<>();
        newArgs.put("start", 1000L);
        newArgs.put("end", 1005L);
        newArgs.put("random", true);

        numberRangeMaker.update(newArgs);

        Thread.sleep(100); // Allow queue to populate

        for (int i = 0; i < 10; i++) {
            Long number = numberRangeMaker.getData();
            assertThat(number).isNotNull().isBetween(1000L, 1005L);
        }
    }
}

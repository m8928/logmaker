package me.blueat.logmaker.plugins.maker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PickMakerTest {

    private PickMaker pickMaker;

    @AfterEach
    void tearDown() {
        if (pickMaker != null && pickMaker.getThread() != null) {
            pickMaker.getThread().interrupt();
        }
    }

    @Test
    @DisplayName("주어진 리스트에서 아이템을 정상적으로 선택하는지 테스트")
    void testPickFromList() {
        List<String> itemList = Arrays.asList("apple", "banana", "cherry");
        Map<String, Object> args = new HashMap<>();
        args.put("picker", itemList);

        pickMaker = new PickMaker("test-picker", "pick", args);
        pickMaker.getThread().start();

        for (int i = 0; i < 10; i++) {
            String picked = pickMaker.getData();
            assertThat(picked).isNotNull().isIn(itemList);
        }
    }

    @Test
    @DisplayName("업데이트 후 새로운 리스트에서 아이템을 선택하는지 테스트")
    void testPickAfterUpdate() throws InterruptedException {
        List<String> initialList = Arrays.asList("a", "b", "c");
        Map<String, Object> initialArgs = new HashMap<>();
        initialArgs.put("picker", initialList);

        pickMaker = new PickMaker("test-update-picker", "pick", initialArgs);
        pickMaker.getThread().start();

        List<String> newList = Arrays.asList("x", "y", "z");
        Map<String, Object> newArgs = new HashMap<>();
        newArgs.put("picker", newList);

        pickMaker.update(newArgs);

        Thread.sleep(100); // Allow queue to populate

        for (int i = 0; i < 10; i++) {
            String picked = pickMaker.getData();
            assertThat(picked).isNotNull().isIn(newList);
        }
    }

    @Test
    @DisplayName("하나의 아이템만 있는 리스트 테스트")
    void testSingleItemList() {
        List<String> itemList = Arrays.asList("single");
        Map<String, Object> args = new HashMap<>();
        args.put("picker", itemList);

        pickMaker = new PickMaker("test-single-item", "pick", args);
        pickMaker.getThread().start();

        String picked = pickMaker.getData();
        assertThat(picked).isNotNull().isEqualTo("single");
    }

    @Test
    @DisplayName("빈 리스트는 백그라운드 스레드를 중단하지 않고 빈 문자열을 반환한다")
    void testEmptyListReturnsBlank() {
        Map<String, Object> args = new HashMap<>();
        args.put("picker", List.of());

        pickMaker = new PickMaker("test-empty-list", "pick", args);
        pickMaker.getThread().start();

        assertThat(pickMaker.getData()).isEmpty();
    }

    @Test
    @DisplayName("빈 리스트는 drain 중에도 빠르게 큐를 채우며 busy-spin하지 않는다")
    void testEmptyListBacksOff() throws InterruptedException {
        Map<String, Object> args = new HashMap<>();
        args.put("picker", List.of());

        pickMaker = new PickMaker("test-empty-list-backoff", "pick", args);
        pickMaker.getThread().start();

        Thread.sleep(250);

        assertThat(pickMaker.getSize()).isLessThan(10);
    }
}

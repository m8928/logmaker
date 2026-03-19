package me.blueat.logmaker.core.maker;

import me.blueat.logmaker.core.config.LogMakerConfig;
import me.blueat.logmaker.core.model.MakerDto;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.core.util.FileUtil;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pf4j.PluginWrapper;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.http.ResponseEntity;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MakerServiceConcurrencyTest {

    @InjectMocks
    private MakerService makerService;

    @Mock
    private SpringPluginManager springPluginManager;

    @Mock
    private LogMakerConfig logMakerConfig;

    private MockedStatic<FileUtil> fileUtilMockedStatic;

    @BeforeEach
    void setUp() {
        when(logMakerConfig.getDataRootPath()).thenReturn(Paths.get("."));
        fileUtilMockedStatic = Mockito.mockStatic(FileUtil.class);
        fileUtilMockedStatic.when(() -> FileUtil.loadFromFile(any(), eq(MakerDto[].class))).thenReturn(new MakerDto[0]);
        makerService.init();
    }

    @AfterEach
    void tearDown() {
        fileUtilMockedStatic.close();
    }

    @Test
    void testConcurrentCreateMaker_noDuplicates() throws Exception {
        // Given
        MakerPlugin makerPlugin = Mockito.mock(MakerPlugin.class);
        when(makerPlugin.getType()).thenReturn("testType");
        when(makerPlugin.getMaker(any(), any())).thenAnswer(inv -> Mockito.mock(Maker.class));

        PluginWrapper pluginWrapper = Mockito.mock(PluginWrapper.class);
        when(pluginWrapper.getPluginId()).thenReturn("testPlugin");

        when(springPluginManager.getPlugins(any())).thenReturn(List.of(pluginWrapper));
        when(springPluginManager.getExtensions(eq(MakerPlugin.class), any())).thenReturn(List.of(makerPlugin));

        makerService.loadPlugin();

        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<ResponseEntity<Result>>> futures = new ArrayList<>();

        // When: 5 threads each try to create a maker with the same name
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                MakerDto makerDto = new MakerDto();
                makerDto.setName("sharedMakerName");
                makerDto.setType("testType");
                startLatch.await();
                return makerService.createMaker(makerDto);
            }));
        }

        startLatch.countDown();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (Future<ResponseEntity<Result>> future : futures) {
            ResponseEntity<Result> response = future.get();
            if (response.getBody().getType() == Result.Type.SUCCESS) {
                successCount.incrementAndGet();
            } else {
                errorCount.incrementAndGet();
            }
        }
        executor.shutdown();

        // Then: only one succeeds, others get error responses
        assertEquals(1, successCount.get(), "Only one thread should successfully create the maker");
        assertEquals(threadCount - 1, errorCount.get(), "All other threads should get error responses");
        assertEquals(1, makerService.getMaker().size(), "Only one maker should exist");
    }
}

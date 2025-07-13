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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MakerServiceTest {

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
    void createMaker() {
        // Given
        MakerDto makerDto = new MakerDto();
        makerDto.setName("testMaker");
        makerDto.setType("testType");

        MakerPlugin makerPlugin = Mockito.mock(MakerPlugin.class);
        when(makerPlugin.getType()).thenReturn("testType");
        when(makerPlugin.getMaker(any(), any())).thenReturn(Mockito.mock(Maker.class));

        PluginWrapper pluginWrapper = Mockito.mock(PluginWrapper.class);
        when(pluginWrapper.getPluginId()).thenReturn("testPlugin");

        when(springPluginManager.getPlugins(any())).thenReturn(List.of(pluginWrapper));
        when(springPluginManager.getExtensions(eq(MakerPlugin.class), any())).thenReturn(List.of(makerPlugin));

        makerService.loadPlugin();

        // When
        ResponseEntity<Result> response = makerService.createMaker(makerDto);

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }
}
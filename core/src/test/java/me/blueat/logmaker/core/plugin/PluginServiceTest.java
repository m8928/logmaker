package me.blueat.logmaker.core.plugin;

import me.blueat.logmaker.core.config.PluginConfig;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.core.sender.SenderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginServiceTest {

    @InjectMocks
    private PluginService pluginService;

    @Mock
    private PluginConfig pluginConfig;

    @Mock
    private MakerService makerService;

    @Mock
    private SenderService senderService;

    @Mock
    private SpringPluginManager springPluginManager;

    @Test
    void uploadPlugin() throws IOException {
        // Given
        MockMultipartFile file = new MockMultipartFile("file", "test.jar", "application/java-archive", new byte[0]);
        SpringPluginManager pluginManager = Mockito.mock(SpringPluginManager.class);
        when(pluginConfig.pluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPluginsRoot()).thenReturn(Paths.get("."));
        when(springPluginManager.loadPlugin(any(Path.class))).thenReturn("testPlugin");

        // When
        ResponseEntity<Result> response = pluginService.uploadPlugin(file);

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }

    @Test
    void deletePlugin() {
        // Given
        when(makerService.getMakerPluginTable()).thenReturn(com.google.common.collect.HashBasedTable.create());
        when(senderService.getSenderPluginTable()).thenReturn(com.google.common.collect.HashBasedTable.create());
        when(springPluginManager.deletePlugin(any(String.class))).thenReturn(true);

        // When
        ResponseEntity<Result> response = pluginService.deletePlugin("testPlugin");

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }

}

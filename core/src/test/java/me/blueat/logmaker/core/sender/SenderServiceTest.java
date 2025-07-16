package me.blueat.logmaker.core.sender;

import me.blueat.logmaker.core.config.LogMakerConfig;
import me.blueat.logmaker.core.model.SenderDto;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.core.util.FileUtil;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import me.blueat.logmaker.plugin.api.sender.Sender;
import me.blueat.logmaker.plugin.api.sender.SenderPlugin;
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
class SenderServiceTest {

    @InjectMocks
    private SenderService senderService;

    @Mock
    private SpringPluginManager springPluginManager;

    @Mock
    private LogMakerConfig logMakerConfig;

    private MockedStatic<FileUtil> fileUtilMockedStatic;

    @BeforeEach
    void setUp() {
        when(logMakerConfig.getDataRootPath()).thenReturn(Paths.get("."));
        fileUtilMockedStatic = Mockito.mockStatic(FileUtil.class);
        fileUtilMockedStatic.when(() -> FileUtil.loadFromFile(any(), eq(SenderDto[].class))).thenReturn(new SenderDto[0]);
        senderService.init();
    }

    @AfterEach
    void tearDown() {
        fileUtilMockedStatic.close();
    }

    @Test
    void createSender() {
        // Given
        SenderDto senderDto = new SenderDto();
        senderDto.setName("testSender");
        senderDto.setType("testType");

        SenderPlugin senderPlugin = Mockito.mock(SenderPlugin.class);
        when(senderPlugin.getType()).thenReturn("testType");
        when(senderPlugin.getSender(any(), any())).thenReturn(Mockito.mock(Sender.class));

        PluginWrapper pluginWrapper = Mockito.mock(PluginWrapper.class);
        when(pluginWrapper.getPluginId()).thenReturn("testPlugin");

        when(springPluginManager.getPlugins(any())).thenReturn(List.of(pluginWrapper));
        when(springPluginManager.getExtensions(eq(SenderPlugin.class))).thenReturn(List.of(senderPlugin));

        senderService.loadPlugin();

        // When
        ResponseEntity<Result> response = senderService.createSender(senderDto);

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }

    @Test
    void createSender_PluginNotFound() {
        // Given
        SenderDto senderDto = new SenderDto();
        senderDto.setName("testSender");
        senderDto.setType("nonExistentType");

        // When
        ResponseEntity<Result> response = senderService.createSender(senderDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void createSender_ArgumentsNotValid() {
        // Given
        SenderDto senderDto = new SenderDto();
        senderDto.setName("testSender");
        senderDto.setType("testType");

        SenderPlugin senderPlugin = Mockito.mock(SenderPlugin.class);
        when(senderPlugin.getType()).thenReturn("testType");
        when(senderPlugin.getSender(any(), any())).thenThrow(new ArgumentsNotValidException());

        PluginWrapper pluginWrapper = Mockito.mock(PluginWrapper.class);
        when(pluginWrapper.getPluginId()).thenReturn("testPlugin");

        when(springPluginManager.getPlugins(any())).thenReturn(List.of(pluginWrapper));
        when(springPluginManager.getExtensions(eq(SenderPlugin.class))).thenReturn(List.of(senderPlugin));

        senderService.loadPlugin();

        // When
        ResponseEntity<Result> response = senderService.createSender(senderDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void createSender_NameAlreadyExists() {
        // Given
        SenderDto senderDto1 = new SenderDto();
        senderDto1.setName("testSender");
        senderDto1.setType("testType");

        SenderDto senderDto2 = new SenderDto();
        senderDto2.setName("testSender");

        SenderPlugin senderPlugin = Mockito.mock(SenderPlugin.class);
        when(senderPlugin.getType()).thenReturn("testType");
        when(senderPlugin.getSender(any(), any())).thenReturn(Mockito.mock(Sender.class));

        PluginWrapper pluginWrapper = Mockito.mock(PluginWrapper.class);
        when(pluginWrapper.getPluginId()).thenReturn("testPlugin");

        when(springPluginManager.getPlugins(any())).thenReturn(List.of(pluginWrapper));
        when(springPluginManager.getExtensions(eq(SenderPlugin.class))).thenReturn(List.of(senderPlugin));

        senderService.loadPlugin();
        senderService.createSender(senderDto1);

        // When
        ResponseEntity<Result> response = senderService.createSender(senderDto2);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void deleteSender_NotFound() {
        // When
        ResponseEntity<Result> response = senderService.deleteSender("nonExistentSender");

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void updateSender_NotFound() {
        // Given
        SenderDto senderDto = new SenderDto();
        senderDto.setName("nonExistentSender");

        // When
        ResponseEntity<Result> response = senderService.updateSender(senderDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void updateSender_Success() {
        // Given
        SenderDto senderDto = new SenderDto();
        senderDto.setName("testSender");
        senderDto.setType("testType");

        SenderPlugin senderPlugin = Mockito.mock(SenderPlugin.class);
        when(senderPlugin.getType()).thenReturn("testType");
        Sender sender = Mockito.mock(Sender.class);
        when(senderPlugin.getSender(any(), any())).thenReturn(sender);

        PluginWrapper pluginWrapper = Mockito.mock(PluginWrapper.class);
        when(pluginWrapper.getPluginId()).thenReturn("testPlugin");

        when(springPluginManager.getPlugins(any())).thenReturn(List.of(pluginWrapper));
        when(springPluginManager.getExtensions(eq(SenderPlugin.class))).thenReturn(List.of(senderPlugin));

        senderService.loadPlugin();
        senderService.createSender(senderDto);

        // When
        ResponseEntity<Result> response = senderService.updateSender(senderDto);

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
        Mockito.verify(sender, Mockito.times(1)).update(any());
    }
}
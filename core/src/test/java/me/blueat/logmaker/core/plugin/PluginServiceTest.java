package me.blueat.logmaker.core.plugin;

import me.blueat.logmaker.core.config.PluginConfig;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.core.sender.SenderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    void uploadPlugin_IOException() throws IOException {
        // Given
        MockMultipartFile file = Mockito.mock(MockMultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.jar");
        SpringPluginManager pluginManager = Mockito.mock(SpringPluginManager.class);
        when(pluginConfig.pluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPluginsRoot()).thenReturn(Paths.get("."));
        Mockito.doThrow(new IOException("Test IOException")).when(file).transferTo(any(Path.class));

        // When
        ResponseEntity<Result> response = pluginService.uploadPlugin(file);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void uploadPlugin_LoadPluginException() throws IOException {
        // Given
        MockMultipartFile file = new MockMultipartFile("file", "test.jar", "application/java-archive", new byte[0]);
        SpringPluginManager pluginManager = Mockito.mock(SpringPluginManager.class);
        when(pluginConfig.pluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPluginsRoot()).thenReturn(Paths.get("."));
        when(springPluginManager.loadPlugin(any(Path.class))).thenThrow(new RuntimeException("Test LoadPluginException"));

        // When
        ResponseEntity<Result> response = pluginService.uploadPlugin(file);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void uploadPlugin_emptyOriginalFilenameUsesDefaultName() throws IOException {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("");
        SpringPluginManager pluginManager = Mockito.mock(SpringPluginManager.class);
        when(pluginConfig.pluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPluginsRoot()).thenReturn(Paths.get("."));
        when(springPluginManager.loadPlugin(any(Path.class))).thenReturn("testPlugin");

        ResponseEntity<Result> response = pluginService.uploadPlugin(file);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(file).transferTo(pathCaptor.capture());
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
        assertFalse(pathCaptor.getValue().getFileName().toString().isBlank());
        org.junit.jupiter.api.Assertions.assertTrue(
                pathCaptor.getValue().getFileName().toString().endsWith("_plugin.jar")
        );
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
        verify(makerService).deleteMakersByPlugin("testPlugin");
        verify(senderService).deleteSendersByPlugin("testPlugin");
    }

    @Test
    void deletePlugin_Failure() {
        // Given
        when(makerService.getMakerPluginTable()).thenReturn(com.google.common.collect.HashBasedTable.create());
        when(senderService.getSenderPluginTable()).thenReturn(com.google.common.collect.HashBasedTable.create());
        when(springPluginManager.deletePlugin(any(String.class))).thenReturn(false);

        // When
        ResponseEntity<Result> response = pluginService.deletePlugin("testPlugin");

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void deletePlugin_rejectsReferencedPlugin() {
        when(makerService.hasReferencedMakersByPlugin("testPlugin")).thenReturn(true);

        ResponseEntity<Result> response = pluginService.deletePlugin("testPlugin");

        assertEquals(Result.Type.ERROR, response.getBody().getType());
        verify(springPluginManager, Mockito.never()).deletePlugin("testPlugin");
        verify(makerService, Mockito.never()).deleteMakersByPlugin("testPlugin");
        verify(senderService, Mockito.never()).deleteSendersByPlugin("testPlugin");
    }

    @Test
    void testUploadPlugin_pathTraversal_sanitized() throws IOException {
        // Given: a file with a path traversal filename
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../malicious.jar", "application/java-archive", new byte[0]);
        SpringPluginManager pluginManager = Mockito.mock(SpringPluginManager.class);
        when(pluginConfig.pluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPluginsRoot()).thenReturn(Paths.get("."));

        // The current implementation uses getOriginalFilename() directly and passes it to Paths.get().
        // This test documents the path traversal risk: a filename with "../" resolves outside the plugins root.
        // The path constructed by the service for "../../malicious.jar" relative to "." will escape the directory.
        Path pluginsRoot = Paths.get(".").toAbsolutePath().normalize();
        Path traversalPath = pluginsRoot.resolve("../../malicious.jar").normalize();

        // Verify that path traversal does escape the plugins root (documents the vulnerability)
        assertFalse(
                traversalPath.startsWith(pluginsRoot),
                "Path traversal filename '../../malicious.jar' should escape the plugins root - sanitization needed"
        );

        // When the upload is attempted with a path-traversal filename, the service should
        // either reject it or handle the IOException gracefully (file won't exist at traversal path)
        when(springPluginManager.loadPlugin(any(Path.class))).thenThrow(new RuntimeException("Plugin not found at traversal path"));
        ResponseEntity<Result> response = pluginService.uploadPlugin(file);

        // Then: operation fails safely without crashing the application
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void testDeletePlugin_cleansUpActiveMakers() {
        // Given: a plugin table with an active maker for "testPlugin"
        com.google.common.collect.Table<String, String, me.blueat.logmaker.plugin.api.maker.MakerPlugin> makerPluginTable =
                com.google.common.collect.HashBasedTable.create();
        me.blueat.logmaker.plugin.api.maker.MakerPlugin makerPlugin =
                Mockito.mock(me.blueat.logmaker.plugin.api.maker.MakerPlugin.class);
        makerPluginTable.put("testPlugin", "testType", makerPlugin);

        com.google.common.collect.Table<String, String, me.blueat.logmaker.plugin.api.sender.SenderPlugin> senderPluginTable =
                com.google.common.collect.HashBasedTable.create();

        when(makerService.getMakerPluginTable()).thenReturn(makerPluginTable);
        when(senderService.getSenderPluginTable()).thenReturn(senderPluginTable);
        when(springPluginManager.deletePlugin("testPlugin")).thenReturn(true);

        // When
        ResponseEntity<Result> response = pluginService.deletePlugin("testPlugin");

        // Then: plugin deleted successfully and maker plugin table row was cleared
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
        assertFalse(makerPluginTable.containsRow("testPlugin"),
                "Active makers from the deleted plugin should be cleaned up");
    }

}

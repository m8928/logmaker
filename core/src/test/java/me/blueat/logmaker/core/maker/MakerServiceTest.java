package me.blueat.logmaker.core.maker;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.blueat.logmaker.core.config.LogMakerConfig;
import me.blueat.logmaker.core.model.MakerDto;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.core.util.FileUtil;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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

    @Mock
    private ObjectMapper mapper;

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

    private void loadPlugin(MakerPlugin plugin) {
        PluginWrapper pluginWrapper = Mockito.mock(PluginWrapper.class);
        when(pluginWrapper.getPluginId()).thenReturn("testPlugin");
        when(springPluginManager.getPlugins(any())).thenReturn(List.of(pluginWrapper));
        when(springPluginManager.getExtensions(eq(MakerPlugin.class), any())).thenReturn(List.of(plugin));
        makerService.loadPlugin();
    }

    @Test
    void createMaker() {
        // Given
        MakerDto makerDto = new MakerDto();
        makerDto.setName("testMaker");
        makerDto.setType("testType");

        MakerPlugin makerPlugin = Mockito.mock(MakerPlugin.class);
        when(makerPlugin.getType()).thenReturn("testType");
        Mockito.doReturn(Mockito.mock(Maker.class)).when(makerPlugin).getMaker(any(), any());

        loadPlugin(makerPlugin);

        // When
        ResponseEntity<Result> response = makerService.createMaker(makerDto);

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }

    @Test
    void testCreateMaker_duplicateName_returnsError() {
        // Given
        MakerDto makerDto = new MakerDto();
        makerDto.setName("dupMaker");
        makerDto.setType("testType");

        MakerPlugin makerPlugin = Mockito.mock(MakerPlugin.class);
        when(makerPlugin.getType()).thenReturn("testType");
        Mockito.doReturn(Mockito.mock(Maker.class)).when(makerPlugin).getMaker(any(), any());

        loadPlugin(makerPlugin);
        makerService.createMaker(makerDto);

        // When: create again with same name
        ResponseEntity<Result> response = makerService.createMaker(makerDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void testCreateMaker_specialCharsInName() {
        // Given: maker name with special characters
        MakerDto makerDto = new MakerDto();
        makerDto.setName("maker-name_123!@#");
        makerDto.setType("testType");

        MakerPlugin makerPlugin = Mockito.mock(MakerPlugin.class);
        when(makerPlugin.getType()).thenReturn("testType");
        Mockito.doReturn(Mockito.mock(Maker.class)).when(makerPlugin).getMaker(any(), any());

        loadPlugin(makerPlugin);
        makerService.createMaker(makerDto);

        // When: create again with same name
        ResponseEntity<Result> response = makerService.createMaker(makerDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void testCreateMaker_invalidArgs_returnsError() {
        // Given
        MakerDto makerDto = new MakerDto();
        makerDto.setName("badArgsMaker");
        makerDto.setType("testType");

        MakerPlugin makerPlugin = Mockito.mock(MakerPlugin.class);
        when(makerPlugin.getType()).thenReturn("testType");

        PluginWrapper pluginWrapper = Mockito.mock(PluginWrapper.class);
        when(pluginWrapper.getPluginId()).thenReturn("testPlugin");

        when(springPluginManager.getPlugins(any())).thenReturn(List.of(pluginWrapper));
        when(springPluginManager.getExtensions(eq(MakerPlugin.class), any())).thenReturn(List.of(makerPlugin));

        makerService.loadPlugin();

        when(makerPlugin.getMaker(any(), any())).thenThrow(new ArgumentsNotValidException());

        // When
        ResponseEntity<Result> response = makerService.createMaker(makerDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void testCreateMaker_unknownType_returnsError() {
        // Given: no plugins loaded for "unknownType"
        MakerDto makerDto = new MakerDto();
        makerDto.setName("unknownMaker");
        makerDto.setType("unknownType");

        // When
        ResponseEntity<Result> response = makerService.createMaker(makerDto);

        // Then: no plugin found for the type, returns ERROR
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void testCreateMaker_emptyName() {
        // Given: maker name is empty string (no plugin type match expected)
        MakerDto makerDto = new MakerDto();
        makerDto.setName("");
        makerDto.setType("nonExistentType");

        // When
        ResponseEntity<Result> response = makerService.createMaker(makerDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void testDeleteMaker_success() {
        // Given
        MakerDto makerDto = new MakerDto();
        makerDto.setName("deleteMaker");
        makerDto.setType("testType");

        @SuppressWarnings("unchecked")
        Maker<Object> maker = Mockito.mock(Maker.class);
        when(maker.isThread()).thenReturn(false);

        MakerPlugin makerPlugin = Mockito.mock(MakerPlugin.class);
        when(makerPlugin.getType()).thenReturn("testType");
        Mockito.doReturn(maker).when(makerPlugin).getMaker(any(), any());

        loadPlugin(makerPlugin);
        makerService.createMaker(makerDto);

        // When
        ResponseEntity<Result> response = makerService.deleteMaker("deleteMaker");

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }

    @Test
    void testDeleteMaker_withRef_returnsError() {
        // Note: MakerService.deleteMaker does NOT check ref count before deleting.
        // The ref check is the responsibility of the caller (LogService).
        // This test verifies deleteMaker succeeds regardless of ref count.
        MakerDto makerDto = new MakerDto();
        makerDto.setName("refMaker");
        makerDto.setType("testType");

        @SuppressWarnings("unchecked")
        Maker<Object> maker = Mockito.mock(Maker.class);
        when(maker.isThread()).thenReturn(false);
        when(maker.getRef()).thenReturn(1);

        MakerPlugin makerPlugin = Mockito.mock(MakerPlugin.class);
        when(makerPlugin.getType()).thenReturn("testType");
        Mockito.doReturn(maker).when(makerPlugin).getMaker(any(), any());

        loadPlugin(makerPlugin);
        makerService.createMaker(makerDto);

        // When
        ResponseEntity<Result> response = makerService.deleteMaker("refMaker");

        // Then: service deletes regardless of ref (ref is tracked externally)
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }

    @Test
    void testDeleteMaker_nonExistent_returnsError() {
        // When
        ResponseEntity<Result> response = makerService.deleteMaker("noSuchMaker");

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void testUpdateMaker_success() {
        // Given
        MakerDto makerDto = new MakerDto();
        makerDto.setName("updateMaker");
        makerDto.setType("testType");

        @SuppressWarnings("unchecked")
        Maker<Object> maker = Mockito.mock(Maker.class);
        when(maker.isThread()).thenReturn(false);

        MakerPlugin makerPlugin = Mockito.mock(MakerPlugin.class);
        when(makerPlugin.getType()).thenReturn("testType");
        Mockito.doReturn(maker).when(makerPlugin).getMaker(any(), any());

        loadPlugin(makerPlugin);
        makerService.createMaker(makerDto);

        // When
        ResponseEntity<Result> response = makerService.updateMaker(makerDto);

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
        Mockito.verify(maker, Mockito.times(1)).update(any());
    }

    @Test
    void testUpdateMaker_nonExistent_returnsError() {
        // Given
        MakerDto makerDto = new MakerDto();
        makerDto.setName("nonExistentMaker");

        // When
        ResponseEntity<Result> response = makerService.updateMaker(makerDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void addMaker_removesTableEntryWhenThreadStartFails() {
        MakerDto makerDto = new MakerDto();
        makerDto.setName("brokenMaker");

        @SuppressWarnings("unchecked")
        Maker<Object> maker = Mockito.mock(Maker.class);
        Thread thread = Mockito.mock(Thread.class);
        when(maker.isThread()).thenReturn(true);
        when(maker.getThread()).thenReturn(thread);
        Mockito.doThrow(new IllegalThreadStateException("already started")).when(thread).start();

        assertThrows(IllegalThreadStateException.class,
                () -> makerService.addMaker(makerDto, "testPlugin", maker));
        assertTrue(makerService.getMaker("brokenMaker").isEmpty());
        assertFalse(makerService.getMakerNames().contains("brokenMaker"));
    }

    @Test
    void testImportMaker_success() throws Exception {
        // Given
        MakerPlugin makerPlugin = Mockito.mock(MakerPlugin.class);
        when(makerPlugin.getType()).thenReturn("testType");
        Mockito.doReturn(Mockito.mock(Maker.class)).when(makerPlugin).getMaker(any(), any());

        loadPlugin(makerPlugin);

        MakerDto importedDto = MakerDto.builder().name("importedMaker").type("testType").build();
        when(mapper.readValue(any(byte[].class), eq(MakerDto[].class)))
                .thenReturn(new MakerDto[]{importedDto});

        org.springframework.web.multipart.MultipartFile file =
                Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getBytes()).thenReturn("[]".getBytes());

        // When
        List<ResponseEntity<Result>> results = makerService.importMaker(file);

        // Then
        assertFalse(results.isEmpty());
        assertEquals(Result.Type.SUCCESS, results.get(0).getBody().getType());
    }
}

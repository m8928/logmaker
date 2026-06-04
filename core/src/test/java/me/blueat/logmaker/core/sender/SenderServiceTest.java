package me.blueat.logmaker.core.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SenderServiceTest {

    @InjectMocks
    private SenderService senderService;

    @Mock
    private SpringPluginManager springPluginManager;

    @Mock
    private LogMakerConfig logMakerConfig;

    @Mock
    private ObjectMapper mapper;

    private MockedStatic<FileUtil> fileUtilMockedStatic;

    @BeforeEach
    void setUp() {
        when(logMakerConfig.getDataRootPath()).thenReturn(java.nio.file.Paths.get("."));
        fileUtilMockedStatic = Mockito.mockStatic(FileUtil.class);
        fileUtilMockedStatic.when(() -> FileUtil.loadFromFile(any(), eq(SenderDto[].class))).thenReturn(new SenderDto[0]);
        senderService.init();
    }

    @AfterEach
    void tearDown() {
        fileUtilMockedStatic.close();
    }

    private SenderPlugin setupPlugin(String type, Sender<?> sender) throws ArgumentsNotValidException {
        SenderPlugin senderPlugin = Mockito.mock(SenderPlugin.class);
        when(senderPlugin.getType()).thenReturn(type);
        Mockito.doReturn(sender).when(senderPlugin).getSender(any(), any());

        PluginWrapper pluginWrapper = Mockito.mock(PluginWrapper.class);
        when(pluginWrapper.getPluginId()).thenReturn("testPlugin");

        when(springPluginManager.getPlugins(any())).thenReturn(List.of(pluginWrapper));
        when(springPluginManager.getExtensions(eq(SenderPlugin.class), any())).thenReturn(List.of(senderPlugin));

        senderService.loadPlugin();
        return senderPlugin;
    }

    @Test
    void createSender() throws Exception {
        // Given
        SenderDto senderDto = new SenderDto();
        senderDto.setName("testSender");
        senderDto.setType("testType");

        setupPlugin("testType", Mockito.mock(Sender.class));

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
    void createSender_ArgumentsNotValid() throws Exception {
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
        when(springPluginManager.getExtensions(eq(SenderPlugin.class), any())).thenReturn(List.of(senderPlugin));

        senderService.loadPlugin();

        // When
        ResponseEntity<Result> response = senderService.createSender(senderDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void createSender_NameAlreadyExists() throws Exception {
        // Given
        SenderDto senderDto1 = new SenderDto();
        senderDto1.setName("testSender");
        senderDto1.setType("testType");

        SenderDto senderDto2 = new SenderDto();
        senderDto2.setName("testSender");

        setupPlugin("testType", Mockito.mock(Sender.class));
        senderService.createSender(senderDto1);

        // When
        ResponseEntity<Result> response = senderService.createSender(senderDto2);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void addSenderClosesDuplicateInstance() {
        SenderDto senderDto = new SenderDto();
        senderDto.setName("dupSender");

        @SuppressWarnings("unchecked")
        Sender<Object> firstSender = Mockito.mock(Sender.class);
        when(firstSender.isThread()).thenReturn(false);
        @SuppressWarnings("unchecked")
        Sender<Object> duplicateSender = Mockito.mock(Sender.class);

        assertTrue(senderService.addSender(senderDto, "testPlugin", firstSender));
        assertFalse(senderService.addSender(senderDto, "testPlugin", duplicateSender));

        verify(duplicateSender).close();
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
    void testDeleteSender_verifiesResourceCleanup() {
        // Given: a thread-based sender that needs cleanup on delete
        SenderDto senderDto = new SenderDto();
        senderDto.setName("threadSender");
        senderDto.setType("testType");

        Thread mockThread = Mockito.mock(Thread.class);
        Sender sender = Mockito.mock(Sender.class);
        when(sender.isThread()).thenReturn(true);
        when(sender.getThread()).thenReturn(mockThread);

        SenderPlugin senderPlugin = Mockito.mock(SenderPlugin.class);
        when(senderPlugin.getType()).thenReturn("testType");
        when(senderPlugin.getSender(any(), any())).thenReturn(sender);

        PluginWrapper pluginWrapper = Mockito.mock(PluginWrapper.class);
        when(pluginWrapper.getPluginId()).thenReturn("testPlugin");

        when(springPluginManager.getPlugins(any())).thenReturn(List.of(pluginWrapper));
        when(springPluginManager.getExtensions(eq(SenderPlugin.class), any())).thenReturn(List.of(senderPlugin));

        senderService.loadPlugin();
        senderService.createSender(senderDto);

        // When
        ResponseEntity<Result> response = senderService.deleteSender("threadSender");

        // Then: delete succeeds and thread interrupt is called for resource cleanup
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
        verify(mockThread, Mockito.times(1)).interrupt();
        verify(sender, Mockito.times(1)).close();
    }

    @Test
    void updateSender_SuccessWithVerify() {
        // Given
        SenderDto senderDto = new SenderDto();
        senderDto.setName("testSender");
        senderDto.setType("testType");

        @SuppressWarnings("unchecked")
        Sender<Object> sender = Mockito.mock(Sender.class);
        setupPlugin("testType", sender);
        senderService.createSender(senderDto);

        // When
        ResponseEntity<Result> response = senderService.updateSender(senderDto);

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
        Mockito.verify(sender, Mockito.times(1)).update(any());
    }

    @Test
    void testCreateSender_duplicateName_returnsError() throws Exception {
        // Given
        SenderDto senderDto = new SenderDto();
        senderDto.setName("dupSender");
        senderDto.setType("testType");

        setupPlugin("testType", Mockito.mock(Sender.class));
        senderService.createSender(senderDto);

        // When: create again with same name
        ResponseEntity<Result> response = senderService.createSender(senderDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void testDeleteSender_success() throws Exception {
        // Given
        SenderDto senderDto = new SenderDto();
        senderDto.setName("deleteSender");
        senderDto.setType("testType");

        @SuppressWarnings("unchecked")
        Sender<Object> sender = Mockito.mock(Sender.class);
        when(sender.isThread()).thenReturn(false);
        setupPlugin("testType", sender);
        senderService.createSender(senderDto);

        // When
        ResponseEntity<Result> response = senderService.deleteSender("deleteSender");

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
        verify(sender, Mockito.times(1)).close();
    }

    @Test
    void deleteSendersByPluginRemovesActiveSenders() {
        SenderDto senderDto = new SenderDto();
        senderDto.setName("pluginSender");

        @SuppressWarnings("unchecked")
        Sender<Object> sender = Mockito.mock(Sender.class);
        when(sender.isThread()).thenReturn(false);

        senderService.addSender(senderDto, "testPlugin", sender);

        senderService.deleteSendersByPlugin("testPlugin");

        assertTrue(senderService.getSender("pluginSender").isEmpty());
        verify(sender).close();
    }

    @Test
    void hasReferencedSendersByPluginDetectsActiveRefs() {
        SenderDto senderDto = new SenderDto();
        senderDto.setName("referencedSender");

        @SuppressWarnings("unchecked")
        Sender<Object> sender = Mockito.mock(Sender.class);
        when(sender.isThread()).thenReturn(false);
        when(sender.getRef()).thenReturn(1);

        senderService.addSender(senderDto, "testPlugin", sender);

        assertTrue(senderService.hasReferencedSendersByPlugin("testPlugin"));
    }

    @Test
    void testDeleteSender_withRef_returnsError() throws Exception {
        SenderDto senderDto = new SenderDto();
        senderDto.setName("refSender");
        senderDto.setType("testType");

        @SuppressWarnings("unchecked")
        Sender<Object> sender = Mockito.mock(Sender.class);
        when(sender.isThread()).thenReturn(false);
        when(sender.getRef()).thenReturn(1);
        setupPlugin("testType", sender);
        senderService.createSender(senderDto);

        // When
        ResponseEntity<Result> response = senderService.deleteSender("refSender");

        assertEquals(Result.Type.ERROR, response.getBody().getType());
        assertTrue(senderService.getSender("refSender").isPresent());
        verify(sender, never()).close();
    }

    @Test
    void testUpdateSender_success() throws Exception {
        // Given
        SenderDto senderDto = new SenderDto();
        senderDto.setName("updateSender");
        senderDto.setType("testType");

        @SuppressWarnings("unchecked")
        Sender<Object> sender = Mockito.mock(Sender.class);
        when(sender.isThread()).thenReturn(false);
        setupPlugin("testType", sender);
        senderService.createSender(senderDto);

        // When
        ResponseEntity<Result> response = senderService.updateSender(senderDto);

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
        Mockito.verify(sender, Mockito.times(1)).update(any());
    }

    @Test
    void addSender_removesTableEntryWhenThreadStartFails() {
        SenderDto senderDto = new SenderDto();
        senderDto.setName("brokenSender");

        @SuppressWarnings("unchecked")
        Sender<Object> sender = Mockito.mock(Sender.class);
        Thread thread = Mockito.mock(Thread.class);
        when(sender.isThread()).thenReturn(true);
        when(sender.getThread()).thenReturn(thread);
        Mockito.doThrow(new IllegalThreadStateException("already started")).when(thread).start();

        assertThrows(IllegalThreadStateException.class,
                () -> senderService.addSender(senderDto, "testPlugin", sender));
        assertTrue(senderService.getSender("brokenSender").isEmpty());
        assertFalse(senderService.getSenderNames().contains("brokenSender"));
        verify(sender, Mockito.times(1)).close();
    }

    @Test
    void addSender_allowsThreadModeSenderWithoutThread() {
        SenderDto senderDto = new SenderDto();
        senderDto.setName("threadlessSender");

        @SuppressWarnings("unchecked")
        Sender<Object> sender = Mockito.mock(Sender.class);
        when(sender.isThread()).thenReturn(true);
        when(sender.getThread()).thenReturn(null);

        assertTrue(senderService.addSender(senderDto, "testPlugin", sender));
        assertTrue(senderService.getSender("threadlessSender").isPresent());
        verify(sender, Mockito.never()).close();
    }

    @Test
    void destroyStopsAndClosesActiveSenders() {
        SenderDto senderDto = new SenderDto();
        senderDto.setName("shutdownSender");

        @SuppressWarnings("unchecked")
        Sender<Object> sender = Mockito.mock(Sender.class);
        Thread thread = Mockito.mock(Thread.class);
        when(sender.isThread()).thenReturn(true);
        when(sender.getThread()).thenReturn(thread);

        assertTrue(senderService.addSender(senderDto, "testPlugin", sender));

        senderService.destroy();

        verify(thread).interrupt();
        verify(sender).close();
        assertTrue(senderService.getSenderNames().isEmpty());
    }

    @Test
    void testImportSender_success() throws Exception {
        // Given
        setupPlugin("testType", Mockito.mock(Sender.class));

        SenderDto importedDto = SenderDto.builder().name("importedSender").type("testType").build();
        when(mapper.readValue(any(byte[].class), eq(SenderDto[].class)))
                .thenReturn(new SenderDto[]{importedDto});

        org.springframework.web.multipart.MultipartFile file =
                Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getBytes()).thenReturn("[]".getBytes());

        // When
        List<ResponseEntity<Result>> results = senderService.importSender(file);

        // Then
        assertFalse(results.isEmpty());
        assertEquals(Result.Type.SUCCESS, results.get(0).getBody().getType());
    }
}

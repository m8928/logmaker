package me.blueat.logmaker.core.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.blueat.logmaker.core.config.LogMakerConfig;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.core.util.FileUtil;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.sender.Sender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @InjectMocks
    private LogService logService;

    @Mock
    private LogMakerConfig logMakerConfig;

    @Mock
    private MakerService makerService;

    @Mock
    private SenderService senderService;

    @Mock
    private ObjectMapper mapper;

    private MockedStatic<FileUtil> fileUtilMockedStatic;

    @BeforeEach
    void setUp() {
        when(logMakerConfig.getDataRootPath()).thenReturn(Paths.get("."));
        fileUtilMockedStatic = Mockito.mockStatic(FileUtil.class);
        fileUtilMockedStatic.when(() -> FileUtil.loadFromFile(any(), eq(LogDto[].class))).thenReturn(new LogDto[0]);
        logService.init();
    }

    @AfterEach
    void tearDown() {
        fileUtilMockedStatic.close();
    }

    @Test
    void createLog() {
        // Given
        LogDto logDto = new LogDto();
        logDto.setName("testLog");
        logDto.setFormat("test format");

        // When
        ResponseEntity<Result> response = logService.createLog(logDto);

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }

    @Test
    void createLog_duplicateName() {
        // Given
        LogDto logDto = new LogDto();
        logDto.setName("testLog");
        logDto.setFormat("test format");
        logService.createLog(logDto);

        // When
        ResponseEntity<Result> response = logService.createLog(logDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void testUpdateLog_success() {
        // Given: create a log with no makers/senders
        LogDto logDto = new LogDto();
        logDto.setName("updateLog");
        logDto.setFormat("static text");
        when(makerService.getMakerNames()).thenReturn(Set.of());
        when(senderService.getSenderNames()).thenReturn(Set.of());
        logService.createLog(logDto);

        // When: update it
        LogDto updated = new LogDto();
        updated.setName("updateLog");
        updated.setFormat("new static text");
        when(makerService.getMakerNames()).thenReturn(Set.of());
        when(senderService.getSenderNames()).thenReturn(Set.of());
        ResponseEntity<Result> response = logService.updateLog(updated);

        // Then
    void testCreateLog_epsZero() {
        // Given
        LogDto logDto = new LogDto();
        logDto.setName("epsZeroLog");
        logDto.setFormat("test format");
        logDto.setEps(0L);

        // When
        ResponseEntity<Result> response = logService.createLog(logDto);

        // Then: EPS=0 is a valid configuration (thread runs but sends nothing per second)
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }

    @Test
    void testUpdateLog_nonExistent_returnsError() {
        // Given
        LogDto logDto = new LogDto();
        logDto.setName("nonExistent");
        logDto.setFormat("format");

        // When
        ResponseEntity<Result> response = logService.updateLog(logDto);

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void testDeleteLog_success() {
        // Given
        LogDto logDto = new LogDto();
        logDto.setName("deleteMe");
        logDto.setFormat("plain text");
        logService.createLog(logDto);

        // When
        ResponseEntity<Result> response = logService.deleteLog("deleteMe");

        // Then
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }

    @Test
    void testDeleteLog_nonExistent_returnsError() {
        // When
        ResponseEntity<Result> response = logService.deleteLog("doesNotExist");

        // Then
        assertEquals(Result.Type.ERROR, response.getBody().getType());
    }

    @Test
    void testDeleteLog_decreasesRefCount() {
        // Given: a maker and sender properly set up so LogThread.init() succeeds
        @SuppressWarnings("unchecked")
        Maker<Object> maker = Mockito.mock(Maker.class);
        @SuppressWarnings("unchecked")
        Sender<Object> sender = Mockito.mock(Sender.class);

        Map.Entry<String, Maker<?>> makerEntry = Map.entry("plugin", maker);
        Map.Entry<String, Sender<?>> senderEntry = Map.entry("plugin", sender);

        when(makerService.getMakerNames()).thenReturn(Set.of("myMaker"));
        when(senderService.getSenderNames()).thenReturn(Set.of("mySender"));
        when(makerService.getMaker("myMaker")).thenReturn(Optional.of(makerEntry));
        when(senderService.getSender("mySender")).thenReturn(Optional.of(senderEntry));
        // getData() is called by getSample (getLogDto) - ST template uses <varName> syntax
        when(maker.getData()).thenReturn("1.2.3.4");

        LogDto logDto = new LogDto();
        logDto.setName("refLog");
        // ST template syntax uses <varName> (angle brackets), not $varName
        logDto.setFormat("<myMaker>");
        logDto.setSender(List.of("mySender"));
        logService.createLog(logDto);

        // When
        logService.deleteLog("refLog");

        // Then ref was decreased (increaseRef during init + decreaseRef during delete)
        Mockito.verify(maker, Mockito.atLeastOnce()).decreaseRef();
        Mockito.verify(sender, Mockito.atLeastOnce()).decreaseRef();
    }

    @Test
    void testGetLog_returnsList() {
        // Given
        LogDto logDto = new LogDto();
        logDto.setName("listLog");
        logDto.setFormat("text");
        logService.createLog(logDto);

        // When
        List<LogDto> logs = logService.getLog();

        // Then
        assertFalse(logs.isEmpty());
        assertEquals("listLog", logs.get(0).getName());
    }

    @Test
    void testGetLog_byName_returnsNull_whenNotFound() {
        // When
        LogThread result = logService.getLog("noSuchLog");

        // Then
        assertNull(result);
    }

    @Test
    void testImportLog_success() throws Exception {
        // Given: mock mapper to return an array with one LogDto
        LogDto importedDto = new LogDto();
        importedDto.setName("importedLog");
        importedDto.setFormat("text");
        when(mapper.readValue(any(byte[].class), eq(LogDto[].class)))
                .thenReturn(new LogDto[]{importedDto});

        org.springframework.web.multipart.MultipartFile file =
                Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getBytes()).thenReturn("[]".getBytes());

        // When
        List<ResponseEntity<Result>> results = logService.importLog(file);

        // Then
        assertFalse(results.isEmpty());
        assertEquals(Result.Type.SUCCESS, results.get(0).getBody().getType());
    }

    @Test
    void testImportLog_duplicateName_returnsError() throws Exception {
        // Given: create a log first
        LogDto existing = new LogDto();
        existing.setName("dupLog");
        existing.setFormat("text");
        logService.createLog(existing);

        // Mock mapper to return a LogDto with the same name
        LogDto dupDto = new LogDto();
        dupDto.setName("dupLog");
        dupDto.setFormat("text");
        when(mapper.readValue(any(byte[].class), eq(LogDto[].class)))
                .thenReturn(new LogDto[]{dupDto});

        org.springframework.web.multipart.MultipartFile file =
                Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getBytes()).thenReturn("[]".getBytes());

        // When
        List<ResponseEntity<Result>> results = logService.importLog(file);

        // Then
        assertFalse(results.isEmpty());
        assertEquals(Result.Type.ERROR, results.get(0).getBody().getType());
    }
}
    void testCreateLog_epsNegative() {
        // Given
        LogDto logDto = new LogDto();
        logDto.setName("epsNegativeLog");
        logDto.setFormat("test format");
        logDto.setEps(-1L);

        // When
        ResponseEntity<Result> response = logService.createLog(logDto);

        // Then: negative EPS creates a log thread but the while loop condition
        // (createCount.get() < logDto.getEps()) is never true, so it behaves like EPS=0
        // The service accepts it (no validation at service layer for EPS range)
        assertEquals(Result.Type.SUCCESS, response.getBody().getType());
    }

}

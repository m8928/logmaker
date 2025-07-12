package me.blueat.logmaker.core.log;

import me.blueat.logmaker.core.config.LogMakerConfig;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.core.util.FileUtil;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

}
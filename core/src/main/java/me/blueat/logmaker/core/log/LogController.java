package me.blueat.logmaker.core.log;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.model.Result;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class LogController {
    private final LogService logService;

    @DeleteMapping("/log/{name}")
    public ResponseEntity<Result> deleteLog(@PathVariable("name") String name) {
        return logService.deleteLog(name);
    }

    @PutMapping("/log/{name}")
    public ResponseEntity<Result> updateLog(@PathVariable("name") String name, @RequestBody @Validated LogDto logDto) {
        logDto.setName(name);
        return logService.updateLog(logDto);
    }

    @PostMapping("/log")
    public ResponseEntity<Result> createLog(@RequestBody @Validated LogDto logDto) {
        return logService.createLog(logDto);
    }

    @PostMapping("/log:import")
    public List<ResponseEntity<Result>> createLog(@RequestBody @Validated LogDto[] logDto) {
        return Arrays.stream(logDto).map(logService::createLog).collect(Collectors.toList());
    }

    @PostMapping(value = "/log:import-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<ResponseEntity<Result>> uploadLogFile(@RequestPart("file") MultipartFile file) {
        return logService.importLog(file);
    }

    @GetMapping("/log")
    public List<LogDto> getLog() {
        return logService.getLog();
    }

    @PostMapping("/log:preview")
    public ResponseEntity<Result> previewLog(@RequestBody LogDto logDto) {
        return logService.previewLog(logDto.getFormat());
    }
}

package me.blueat.logmaker.core.log;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.util.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    public Result deleteLog(@PathVariable("name") String name) {
        return logService.deleteLog(name);
    }

    @PutMapping("/log/{name}")
    public Result updateLog(@PathVariable("name") String name, @RequestBody @Validated LogDto logDto) {
        logDto.setName(name);
        return logService.updateLog(logDto);
    }

    @PostMapping("/log")
    public Result createLog(@RequestBody @Validated LogDto logDto) {
        return logService.createLog(logDto);
    }

    @PostMapping("/log:import")
    public List<Result> createLog(@RequestBody @Validated LogDto[] logDto) {
        return Arrays.stream(logDto).map(dto -> logService.createLog(dto)).collect(Collectors.toList());
    }

    @GetMapping("/log")
    public List<LogDto> getLog() {
        return Arrays.stream(logService.getLog()).map(LogThread::getLogDto).collect(Collectors.toList());
    }

    @PostMapping("/log:preview")
    public LogDto previewLog(@RequestBody LogDto logDto) {
        logDto.setSample(logService.previewLog(logDto.getFormat()));
        return logDto;
    }
}

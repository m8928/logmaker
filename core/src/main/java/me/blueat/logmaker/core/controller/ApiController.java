package me.blueat.logmaker.core.controller;

import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.config.PluginConfig;
import me.blueat.logmaker.core.loggen.LogDto;
import me.blueat.logmaker.core.loggen.LogService;
import me.blueat.logmaker.core.loggen.LogThread;
import me.blueat.logmaker.core.maker.MakerDto;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.support.Result;
import me.blueat.logmaker.plugin.api.Maker;
import me.blueat.logmaker.plugin.api.MakerPlugin;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ApiController {
    private final LogService logService;
    private final MakerService makerService;
    private final PluginConfig pluginConfig;

    @GetMapping("/plugin/maker")
    public List<MakerDto> getSupportMaker() {
        List<MakerDto> result = new ArrayList<>();

        makerService.getMakerPluginMap().values().forEach((v) -> {
            MakerDto.MakerDtoBuilder makerDtoBuilder = MakerDto.builder().type(v.getType());
            makerDtoBuilder.args(Maps.newLinkedHashMap(v.getMakerArgsMap()));
            result.add(makerDtoBuilder.build());
        });

        return result;
    }

    @PostMapping(path="/plugin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result uploadFile(@RequestPart("file") MultipartFile file) {
        log.info("{}",file);
        try {
            if(file.getSize() > 0) {
                Path pluginPath = Path.of(pluginConfig.pluginManager().getPluginsRoot().toString(),file.getOriginalFilename());
                file.transferTo(pluginPath);
                makerService.loadPlugin(pluginPath);
            }
            return Result.createResultSet(Result.Type.SUCCESS);
        }
        catch (Exception ioe) {
            return Result.createResultSet(Result.Type.ERROR);
        }
    }

    @GetMapping("/maker")
    public List<MakerDto> getMaker() {
        return makerService.getMaker();
    }

    @DeleteMapping("/maker/{name}")
    public Result removeMaker(@PathVariable("name") String name) {
        Result result;
        if (makerService.removeMaker(name)) {
            result = Result.createResultSet(Result.Type.SUCCESS);
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR);
        }

        return result;
    }

    @RequestMapping(method = RequestMethod.POST, path = "/maker")
    public Result createMaker(@RequestBody MakerDto makerDto) {
        Result result;
        MakerPlugin makerFactory = makerService.getMakerPluginMap().get(makerDto.getType());

        if (makerFactory != null) {
            Maker maker = makerFactory.getMaker(makerDto.getType(), makerDto.getArgs());

            if (maker != null) {
                if (makerService.addMaker(makerDto, maker)) {
                    result = Result.createResultSet(Result.Type.SUCCESS);
                }
                else {
                    result = Result.createResultSet(Result.Type.ERROR, String.format("%s is already used", makerDto.getName()));
                }
            }
            else {
                result = Result.createResultSet(Result.Type.ERROR, String.format("%s is invalid", makerDto.getName()));
            }
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR, String.format("%s is not support", makerDto.getType()));
        }

        return result;
    }

    @DeleteMapping("/log/{name}")
    public Result removeLog(@PathVariable("name") String name) {
        Result result;
        if (logService.removeLog(name)) {
            result = Result.createResultSet(Result.Type.SUCCESS);
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR);
        }

        return result;
    }

    @RequestMapping(method = RequestMethod.POST, path = "/log")
    public Result createLog(@RequestBody LogDto logDto) {
        return logService.createLog(logDto);
    }

    @GetMapping("/log")
    public List<LogDto> getLog() {
        return Arrays.stream(logService.getLog()).map(LogThread::getLogDto).collect(Collectors.toList());
    }
}

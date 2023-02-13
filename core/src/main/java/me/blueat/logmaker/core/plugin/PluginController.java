package me.blueat.logmaker.core.plugin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.MakerDto;
import me.blueat.logmaker.core.model.PluginDto;
import me.blueat.logmaker.core.model.SenderDto;
import me.blueat.logmaker.core.model.Result;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PluginController {
    private final PluginService pluginService;

    @GetMapping("/plugin")
    public List<PluginDto> getPlugin() {
        return pluginService.getPlugin();
    }

    @GetMapping("/plugin/maker")
    public List<MakerDto> getMaker() {
        return pluginService.getMaker();
    }

    @GetMapping("/plugin/sender")
    public List<SenderDto> getSender() {
        return pluginService.getSender();
    }

    @DeleteMapping("/plugin/{name}")
    public ResponseEntity<Result> deletePlugin(@PathVariable("name") String name) {
        return pluginService.deletePlugin(name);
    }

    @PostMapping(path="/plugin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result> uploadPlugin(@RequestPart("file") MultipartFile file) {
        return pluginService.uploadPlugin(file);
    }
}

package me.blueat.logmaker.core.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.SenderDto;
import me.blueat.logmaker.core.util.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SenderController {
    private final SenderService senderService;

    @GetMapping("/sender")
    public List<SenderDto> geSender() {
        return senderService.getSender();
    }

    @DeleteMapping("/sender/{name}")
    public Result deleteSender(@PathVariable("name") String name) {
        return senderService.deleteSender(name);
    }

    @PostMapping("/sender")
    public Result createSender(@RequestBody @Validated SenderDto senderDto) {
        return senderService.createSender(senderDto);
    }

    @PutMapping("/sender/{name}")
    public Result updateSender(@PathVariable("name") String name, @RequestBody @Validated SenderDto senderDto) {
        senderDto.setName(name);
        return senderService.updateSender(senderDto);
    }
}

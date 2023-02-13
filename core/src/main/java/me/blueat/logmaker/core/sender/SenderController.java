package me.blueat.logmaker.core.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.SenderDto;
import me.blueat.logmaker.core.model.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
    public ResponseEntity<Result> deleteSender(@PathVariable("name") String name) {
        return senderService.deleteSender(name);
    }

    @PostMapping("/sender")
    public ResponseEntity<Result> createSender(@RequestBody @Validated SenderDto senderDto) {
        return senderService.createSender(senderDto);
    }

    @PostMapping("/sender:import")
    public List<ResponseEntity<Result>> createMaker(@RequestBody @Validated SenderDto[] senderDto) {
        return Arrays.stream(senderDto).map(dto -> senderService.createSender(dto)).collect(Collectors.toList());
    }

    @PutMapping("/sender/{name}")
    public ResponseEntity<Result> updateSender(@PathVariable("name") String name, @RequestBody @Validated SenderDto senderDto) {
        senderDto.setName(name);
        return senderService.updateSender(senderDto);
    }
}

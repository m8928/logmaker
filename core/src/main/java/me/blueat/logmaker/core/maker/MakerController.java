package me.blueat.logmaker.core.maker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.MakerDto;
import me.blueat.logmaker.core.util.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class MakerController {
    private final MakerService makerService;

    @GetMapping("/maker")
    public List<MakerDto> getMaker() {
        return makerService.getMaker();
    }

    @DeleteMapping("/maker/{name}")
    public Result deleteMaker(@PathVariable("name") String name) {
        return makerService.deleteMaker(name);
    }

    @PostMapping("/maker")
    public Result createMaker(@RequestBody @Validated MakerDto makerDto) {
        return makerService.createMaker(makerDto);
    }

    @PutMapping("/maker/{name}")
    public Result updateMaker(@PathVariable("name") String name, @RequestBody @Validated MakerDto makerDto) {
        makerDto.setName(name);
        return makerService.updateMaker(makerDto);
    }
}

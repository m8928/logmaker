package me.blueat.logmaker.core.maker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.MakerDto;
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
public class MakerController {
    private final MakerService makerService;

    @GetMapping("/maker")
    public List<MakerDto> getMaker() {
        return makerService.getMaker();
    }

    @DeleteMapping("/maker/{name}")
    public ResponseEntity<Result> deleteMaker(@PathVariable("name") String name) {
        return makerService.deleteMaker(name);
    }

    @PostMapping("/maker")
    public ResponseEntity<Result> createMaker(@RequestBody @Validated MakerDto makerDto) {
        return makerService.createMaker(makerDto);
    }

    @PostMapping("/maker:import")
    public List<ResponseEntity<Result>> createMaker(@RequestBody @Validated MakerDto[] makerDto) {
        return Arrays.stream(makerDto).map(makerService::createMaker).collect(Collectors.toList());
    }

    @PostMapping(value = "/maker:import-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<ResponseEntity<Result>> uploadMakerFile(@RequestBody MultipartFile file) {
        return makerService.importMaker(file);
    }

    @PutMapping("/maker/{name}")
    public ResponseEntity<Result> updateMaker(@PathVariable("name") String name, @RequestBody @Validated MakerDto makerDto) {
        makerDto.setName(name);
        return makerService.updateMaker(makerDto);
    }
}

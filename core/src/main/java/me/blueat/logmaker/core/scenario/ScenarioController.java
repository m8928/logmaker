package me.blueat.logmaker.core.scenario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ScenarioController {

    private final ScenarioService scenarioService;

    @GetMapping("/scenario")
    public List<ScenarioDto> getScenarios() {
        return scenarioService.getScenarios();
    }

    @PostMapping("/scenario")
    public ResponseEntity<Result> createScenario(@RequestBody ScenarioDto scenarioDto) {
        return scenarioService.createScenario(scenarioDto);
    }

    @PutMapping("/scenario/{name}")
    public ResponseEntity<Result> updateScenario(@PathVariable("name") String name,
                                                  @RequestBody ScenarioDto scenarioDto) {
        return scenarioService.updateScenario(name, scenarioDto);
    }

    @DeleteMapping("/scenario/{name}")
    public ResponseEntity<Result> deleteScenario(@PathVariable("name") String name) {
        return scenarioService.deleteScenario(name);
    }

    @PostMapping("/scenario/{name}:start")
    public ResponseEntity<Result> startScenario(@PathVariable("name") String name) {
        return scenarioService.startScenario(name);
    }

    @PostMapping("/scenario/{name}:stop")
    public ResponseEntity<Result> stopScenario(@PathVariable("name") String name) {
        return scenarioService.stopScenario(name);
    }
}

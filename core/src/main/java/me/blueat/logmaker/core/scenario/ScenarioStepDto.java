package me.blueat.logmaker.core.scenario;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class ScenarioStepDto {
    private String logName;
    private int repeat = 1;
    private long delayMinMs = 0;
    private long delayMaxMs = 0;
    private Map<String, String> overrides = new HashMap<>();
}

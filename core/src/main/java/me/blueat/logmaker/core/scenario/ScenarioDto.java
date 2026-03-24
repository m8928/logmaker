package me.blueat.logmaker.core.scenario;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class ScenarioDto {
    private String name;
    private String description;
    private Map<String, String> sharedVariables = new HashMap<>();
    private List<ScenarioStepDto> steps = new ArrayList<>();
    private List<String> senders = new ArrayList<>();
    private int eps;
    private int loopCount = 0;

    // Runtime fields
    private boolean status;
    private long count;
    private long currentEps;
}

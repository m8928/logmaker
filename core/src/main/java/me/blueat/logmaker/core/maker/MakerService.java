package me.blueat.logmaker.core.maker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerPlugin;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MakerService {
    private HashMap<String, Maker<?>> makerMap;
    private HashMap<String, MakerPlugin> makerPluginMap;

    private final SpringPluginManager springPluginManager;

    @PostConstruct
    protected void init() {
        makerMap = new HashMap<>();
        makerPluginMap = new HashMap<>();

        springPluginManager.getExtensions(MakerPlugin.class).forEach(makerPlugin ->
                makerPluginMap.put(makerPlugin.getType(), makerPlugin));

        log.info("{}", springPluginManager.getExtensions(MakerPlugin.class));
    }

    public HashMap<String, Maker<?>> getMakerMap() {
        return makerMap;
    }

    public HashMap<String, MakerPlugin> getMakerPluginMap() {
        return makerPluginMap;
    }

    public List<MakerDto> getMaker() {
        return makerMap.values().stream()
                .map(v -> MakerDto.builder()
                        .name(v.getMakerName())
                        .type(v.getType())
                        .args(new HashMap<>())
                        .sample(v.getData())
                        .size(v.getSize()).build())
                .collect(Collectors.toList());
    }

    public Maker<?> getMaker(String name) {
        return makerMap.get(name);
    }

    public boolean removeMaker(String name) {
        Optional<Maker> optionalMaker = Optional.ofNullable(makerMap.get(name));

        if (optionalMaker.isPresent()) {
            if (optionalMaker.get().isThread()) {
                ((Thread)makerMap.get(optionalMaker.get())).interrupt();
            }
            makerMap.remove(name);
            return true;
        }

        return false;
    }

    public boolean addMaker(MakerDto makerDto, Maker maker) {
        Optional<Maker> optionalMaker = Optional.ofNullable(makerMap.get(makerDto.getName()));

        if (optionalMaker.isEmpty()) {
            makerMap.put(makerDto.getName(), maker);
            if (maker.isThread()) {
                ((Thread)maker).start();
            }
            return true;
        }
        else {
            return false;
        }
    }

    public Set<String> getMakerNames() {
        Set<String> makerNames = new HashSet<>();
        makerMap.keySet().forEach((k) -> makerNames.add(k));
        return makerNames;
    }

    public void loadPlugin(Path path) {
        String pluginId = springPluginManager.loadPlugin(path);
        springPluginManager.startPlugin(pluginId);

        springPluginManager.getExtensions(MakerPlugin.class, pluginId).forEach(makerPlugin -> {
            makerPluginMap.put(makerPlugin.getType(), makerPlugin);
        });
    }
}

package me.blueat.logmaker.core.maker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.Maker;
import me.blueat.logmaker.plugin.api.MakerPlugin;
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
    private HashMap<MakerDto, Maker<?>> makerMap;
    private HashMap<String, MakerPlugin> makerPluginMap;

    private final SpringPluginManager springPluginManager;

    @PostConstruct
    protected void init() {
        makerMap = new HashMap<>();
        makerPluginMap = new HashMap<>();

        springPluginManager.getExtensions(MakerPlugin.class).forEach(makerPlugin -> {
            makerPluginMap.put(makerPlugin.getType(), makerPlugin);
        });

        log.info("{}", springPluginManager.getExtensions(MakerPlugin.class));
    }

    public HashMap<MakerDto, Maker<?>> getMakerMap() {
        return makerMap;
    }

    public HashMap<String, MakerPlugin> getMakerPluginMap() {
        return makerPluginMap;
    }

    public List<MakerDto> getMaker() {
        return makerMap.entrySet().stream().map(e -> {
            e.getKey().setSample(e.getValue().getData());
            e.getKey().setSize(e.getValue().getSize());
            return e.getKey();
        }).collect(Collectors.toList());
    }

    public Maker<?> getMaker(String name) {
        Optional<MakerDto> optionalMakerDto = makerMap.keySet().stream().filter(f -> f.getName().equals(name)).findAny();
        return makerMap.get(makerMap.containsKey(optionalMakerDto.get()));
    }

    public boolean removeMaker(String name) {
        Optional<MakerDto> optionalMakerDto = makerMap.keySet().stream().filter(f -> f.getName().equals(name)).findAny();

        if (optionalMakerDto.isPresent()) {
            if (makerMap.get(optionalMakerDto.get()).isThread()) {
                ((Thread)makerMap.get(optionalMakerDto.get())).interrupt();
            }
            makerMap.remove(optionalMakerDto.get());
            return true;
        }

        return false;
    }

    public boolean addMaker(MakerDto makerDto, Maker maker) {
        Optional<MakerDto> optionalMakerDto = makerMap.keySet().stream().filter(f -> f.getName().equals(makerDto.getName())).findAny();

        if (optionalMakerDto.isEmpty()) {
            makerMap.put(makerDto, maker);
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
        makerMap.keySet().forEach((k) -> makerNames.add(k.getName()));
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

package me.blueat.logmaker.core.maker;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.MakerDto;
import me.blueat.logmaker.core.util.Result;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerPlugin;
import org.pf4j.PluginState;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Data
public class MakerService {
    private final SpringPluginManager springPluginManager;
    private Table<String, String, Maker<?>> makerTable;
    private Table<String, String, MakerPlugin> makerPluginTable;

    @PostConstruct
    protected void init() {
        makerTable = HashBasedTable.create();
        makerPluginTable = HashBasedTable.create();
        loadPlugin();
    }

    public List<MakerDto> getMaker() {
        return makerTable.cellSet().stream().map(v ->
            MakerDto.builder()
                    .name(v.getColumnKey())
                    .type(v.getValue().getType())
                    .args(v.getValue().getArgs())
                    .sample(v.getValue().getData())
                    .ref(v.getValue().getRef())
                    .size(v.getValue().getSize()).build()).collect(Collectors.toList());
    }

    public Optional<Map.Entry<String, Maker<?>>> getMaker(String name) {
        return makerTable.column(name).entrySet().stream().findFirst();
    }

    public Optional<Map.Entry<String, MakerPlugin>> getMakerPlugin(String name) {
        return makerPluginTable.column(name).entrySet().stream().findFirst();
    }

    public Result deleteMaker(String name) {
        Optional<Map.Entry<String, Maker<?>>> existsMaker = getMaker(name);

        if (existsMaker.isPresent()) {
            if (existsMaker.get().getValue().isThread()) {
                existsMaker.get().getValue().getThread().interrupt();
            }
            makerTable.remove(existsMaker.get().getKey(), name);
            return Result.createResultSet(Result.Type.SUCCESS);
        }

        return Result.createResultSet(Result.Type.ERROR);
    }

    public Result createMaker(MakerDto makerDto) {
        Result result;
        Optional<Map.Entry<String, MakerPlugin>> makerPlugin = getMakerPlugin(makerDto.getType());

        if (makerPlugin.isPresent()) {
            try {
                Maker maker = makerPlugin.get().getValue().getMaker(makerDto.getName(), makerDto.getArgs());

                if (maker != null) {
                    if (addMaker(makerDto, makerPlugin.get().getKey(), maker)) {
                        result = Result.createResultSet(Result.Type.SUCCESS);
                    }
                    else {
                        result = Result.createResultSet(Result.Type.ERROR, String.format("%s is already used", makerDto.getName()));
                    }
                }
                else {
                    result = Result.createResultSet(Result.Type.ERROR, String.format("%s is invalid", makerDto.getName()));
                }
            }
            catch (ArgumentsNotValidException anve) {
                result = Result.createResultSet(Result.Type.ERROR, String.format("%s is invalid", makerDto.getName()));
            }
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR, String.format("%s is not support", makerDto.getType()));
        }

        return result;
    }

    public boolean addMaker(MakerDto makerDto, String pluginId, Maker maker) {
        Optional<Map.Entry<String, Maker<?>>> existsMaker = getMaker(makerDto.getName());

        if (existsMaker.isEmpty()) {
            makerTable.put(pluginId, makerDto.getName(), maker);
            if (maker.isThread()) {
                maker.getThread().start();
            }
            return true;
        }
        else {
            return false;
        }
    }

    public Set<String> getMakerNames() {
        Set<String> makerNames = new HashSet<>();
        makerTable.columnKeySet().forEach((k) -> makerNames.add(k));
        return makerNames;
    }

    public void loadPlugin() {
        loadPlugin(null);
    }
    public void loadPlugin(String pluginId) {
        springPluginManager.getPlugins(PluginState.STARTED).stream()
                .filter(p -> (pluginId == null) || (pluginId != null && p.getPluginId().equals(pluginId)))
                .forEach(pluginWrapper -> springPluginManager.getExtensions(MakerPlugin.class)
                        .forEach(makerPlugin -> {
                            log.info("{}", makerPlugin.getType());
                            getMakerPluginTable().put(pluginWrapper.getPluginId(), makerPlugin.getType(), makerPlugin);
        }));
    }

    public Result updateMaker(MakerDto makerDto) {
        Result result;
        Optional<Map.Entry<String, Maker<?>>> existsMaker = getMaker(makerDto.getName());

        if (existsMaker.isPresent()) {
            existsMaker.get().getValue().update(makerDto.getArgs());
            result = Result.createResultSet(Result.Type.SUCCESS);
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR);
        }

        return result;
    }
}

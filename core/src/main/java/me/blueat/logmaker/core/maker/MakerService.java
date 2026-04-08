package me.blueat.logmaker.core.maker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import jakarta.annotation.PostConstruct;
import jakarta.xml.bind.DataBindingException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.config.LogMakerConfig;
import me.blueat.logmaker.core.model.MakerDto;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerPlugin;
import org.pf4j.PluginState;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static me.blueat.logmaker.core.util.FileUtil.loadFromFile;
import static me.blueat.logmaker.core.util.FileUtil.saveToFile;

@Service
@RequiredArgsConstructor
@Slf4j
@Getter
@Order(1)
public class MakerService {
    private final SpringPluginManager springPluginManager;
    private final LogMakerConfig logMakerConfig;
    private final ObjectMapper mapper;
    private Table<String, String, Maker<?>> makerTable;
    private Table<String, String, MakerPlugin> makerPluginTable;
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> makerNameRegistry = new java.util.concurrent.ConcurrentHashMap<>();

    @PostConstruct
    protected void init() {
        makerTable = Tables.synchronizedTable(HashBasedTable.create());
        makerPluginTable = Tables.synchronizedTable(HashBasedTable.create());
        loadPlugin();
        Arrays.stream(Objects.requireNonNull(loadFromFile(String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "makers.json")
                        , MakerDto[].class)))
                .forEach(makerDto -> createMaker(makerDto, true));
        log.info("Initialized Maker Service");
    }

    public List<MakerDto> getMaker() {
        return makerTable.cellSet().stream().map(v ->
                MakerDto.builder()
                        .name(v.getColumnKey())
                        .type(v.getValue().getType())
                        .args(v.getValue().getArgs())
                        .sample(v.getValue().getData())
                        .ref(v.getValue().getRef())
                        .size(v.getValue().getSize())
                        .regTime(v.getValue().getRegTime()).build())
                .sorted(Comparator.comparing(MakerDto::getRegTime).reversed())
                .collect(Collectors.toList());
    }

    public Optional<Map.Entry<String, Maker<?>>> getMaker(String name) {
        return makerTable.column(name).entrySet().stream().findFirst();
    }

    public Optional<Map.Entry<String, MakerPlugin>> getMakerPlugin(String name) {
        if (makerPluginTable.containsColumn(name)) {
            return makerPluginTable.column(name).entrySet().stream().findFirst();
        }
        else {
            return Optional.empty();
        }
    }

    public ResponseEntity<Result> deleteMaker(String name) {
        if (makerNameRegistry.remove(name) == null) {
            return Result.createResultSet(Result.Type.ERROR, "Maker does not exist");
        }
        Optional<Map.Entry<String, Maker<?>>> existsMaker = getMaker(name);
        if (existsMaker.isPresent()) {
            if (existsMaker.get().getValue().isThread()) {
                existsMaker.get().getValue().getThread().interrupt();
            }
            makerTable.remove(existsMaker.get().getKey(), name);
        }
        saveToFile(getMaker(), String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "makers.json"));
        return Result.createResultSet(Result.Type.SUCCESS, "Successfully deleted maker");
    }

    public List<ResponseEntity<Result>> importMaker(MultipartFile json) {
        try {
            MakerDto[] makers = mapper.readValue(json.getBytes(), MakerDto[].class);
            return Arrays.stream(makers).map(this::createMaker).collect(Collectors.toList());
        }
        catch (IOException | DataBindingException e) {
            return Lists.newArrayList(Result.createResultSet(Result.Type.ERROR, "Maker file import failed"));
        }
    }

    public ResponseEntity<Result> createMaker(MakerDto makerDto) {
        return createMaker(makerDto, false);
    }

    public ResponseEntity<Result> createMaker(MakerDto makerDto, boolean isImport) {
        ResponseEntity<Result> result;
        Optional<Map.Entry<String, MakerPlugin>> makerPlugin = getMakerPlugin(makerDto.getType());

        if (makerPlugin.isPresent()) {
            try {
                Maker maker = makerPlugin.get().getValue().getMaker(makerDto.getName(), makerDto.getArgs());

                if (maker != null) {
                    if (addMaker(makerDto, makerPlugin.get().getKey(), maker)) {
                        result = Result.createResultSet(Result.Type.SUCCESS, "Successful maker registration");

                        if (!isImport) {
                            saveToFile(getMaker(), String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "makers.json"));
                        }
                    }
                    else {
                        result = Result.createResultSet(Result.Type.ERROR, String.format("%s is the maker name already in use", makerDto.getName()));
                    }
                }
                else {
                    result = Result.createResultSet(Result.Type.ERROR, String.format("%s is an unavailable maker type", makerDto.getType()));
                }
            }
            catch (ArgumentsNotValidException anve) {
                result = Result.createResultSet(Result.Type.ERROR, String.format("Invalid maker argument (%s)", makerDto.getArgs()));
            }
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR, String.format("%s is an unavailable maker type", makerDto.getType()));
        }

        return result;
    }

    public boolean addMaker(MakerDto makerDto, String pluginId, Maker maker) {
        if (makerNameRegistry.putIfAbsent(makerDto.getName(), Boolean.TRUE) != null) {
            return false;
        }
        try {
            makerTable.put(pluginId, makerDto.getName(), maker);
            if (maker.isThread()) {
                maker.getThread().start();
            }
            return true;
        } catch (Exception e) {
            makerNameRegistry.remove(makerDto.getName());
            throw e;
        }
    }

    public Set<String> getMakerNames() {
        return new HashSet<>(makerTable.columnKeySet());
    }

    public void loadPlugin() {
        loadPlugin(null);
    }
    public void loadPlugin(String pluginId) {
        springPluginManager.getPlugins(PluginState.STARTED).stream()
                .filter(p -> pluginId == null || p.getPluginId().equals(pluginId))
                .forEach(pluginWrapper -> springPluginManager.getExtensions(MakerPlugin.class, pluginWrapper.getPluginId())
                        .forEach(makerPlugin -> {
                            log.info("{}/{}", makerPlugin.getType(), getMakerPluginTable().contains(pluginWrapper.getPluginId(), makerPlugin.getType()));
                            if (!getMakerPluginTable().contains(pluginWrapper.getPluginId(), makerPlugin.getType())) {
                                getMakerPluginTable().put(pluginWrapper.getPluginId(), makerPlugin.getType(), makerPlugin);
                            }
                            else {
                                log.warn("Plugin is already loaded. id={}, type={}", pluginWrapper.getPluginId(), makerPlugin.getType());
                            }
        }));
    }

    public ResponseEntity<Result> updateMaker(MakerDto makerDto) {
        ResponseEntity<Result> result;
        Optional<Map.Entry<String, Maker<?>>> existsMaker = getMaker(makerDto.getName());

        if (existsMaker.isPresent()) {
            existsMaker.get().getValue().update(makerDto.getArgs());
            saveToFile(getMaker(), String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "makers.json"));
            result = Result.createResultSet(Result.Type.SUCCESS, "Successfully updated maker");
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR, "Update maker failed");
        }

        return result;
    }
}

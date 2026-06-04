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

    private record MakerSnapshot(String name, Maker<?> maker) {
    }

    @PostConstruct
    protected void init() {
        makerTable = Tables.synchronizedTable(HashBasedTable.create());
        makerPluginTable = Tables.synchronizedTable(HashBasedTable.create());
        loadPlugin();
        MakerDto[] loadedMakers = loadFromFile(makerStoragePath(), MakerDto[].class);
        if (loadedMakers != null) {
            Arrays.stream(loadedMakers).forEach(makerDto -> createMaker(makerDto, true));
        }
        log.info("Initialized Maker Service");
    }

    public List<MakerDto> getMaker() {
        List<MakerSnapshot> snapshot;
        synchronized (makerTable) {
            snapshot = makerTable.cellSet().stream()
                    .map(v -> new MakerSnapshot(v.getColumnKey(), v.getValue()))
                    .toList();
        }

        return snapshot.stream().map(v ->
                MakerDto.builder()
                        .name(v.name())
                        .type(v.maker().getType())
                        .args(v.maker().getArgs())
                        .sample(v.maker().getData())
                        .ref(v.maker().getRef())
                        .size(v.maker().getSize())
                        .regTime(v.maker().getRegTime()).build())
                .sorted(Comparator.comparing(MakerDto::getRegTime).reversed())
                .collect(Collectors.toList());
    }

    public Optional<Map.Entry<String, Maker<?>>> getMaker(String name) {
        synchronized (makerTable) {
            return makerTable.column(name).entrySet().stream()
                    .findFirst()
                    .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
        }
    }

    public Optional<Map.Entry<String, MakerPlugin>> getMakerPlugin(String name) {
        synchronized (makerPluginTable) {
            if (makerPluginTable.containsColumn(name)) {
                return makerPluginTable.column(name).entrySet().stream()
                        .findFirst()
                        .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            }
            else {
                return Optional.empty();
            }
        }
    }

    public ResponseEntity<Result> deleteMaker(String name) {
        if (makerNameRegistry.remove(name) == null) {
            return Result.createResultSet(Result.Type.ERROR, "Maker does not exist");
        }
        Optional<Map.Entry<String, Maker<?>>> existsMaker = getMaker(name);
        if (existsMaker.isPresent()) {
            Maker<?> maker = existsMaker.get().getValue();
            stopMakerThread(name, maker);
            try {
                maker.close();
            } catch (Exception e) {
                log.warn("Failed to close maker: {}", name, e);
            } finally {
                synchronized (makerTable) {
                    makerTable.remove(existsMaker.get().getKey(), name);
                }
            }
        }
        saveToFile(getMaker(), makerStoragePath());
        return Result.createResultSet(Result.Type.SUCCESS, "Successfully deleted maker");
    }

    public void deleteMakersByPlugin(String pluginId) {
        List<String> makerNames;
        synchronized (makerTable) {
            makerNames = new ArrayList<>(makerTable.row(pluginId).keySet());
        }
        makerNames.forEach(this::deleteMaker);
    }

    public boolean hasReferencedMakersByPlugin(String pluginId) {
        synchronized (makerTable) {
            return makerTable.row(pluginId).values().stream()
                    .anyMatch(maker -> maker.getRef() > 0);
        }
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
                            saveToFile(getMaker(), makerStoragePath());
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
            try {
                maker.close();
            } catch (Exception e) {
                log.warn("Failed to close maker after duplicate name check: {}", makerDto.getName(), e);
            }
            return false;
        }
        try {
            synchronized (makerTable) {
                makerTable.put(pluginId, makerDto.getName(), maker);
            }
            startMakerThread(makerDto.getName(), maker);
            return true;
        } catch (Exception e) {
            try {
                maker.close();
            } catch (Exception closeException) {
                log.warn("Failed to close maker after registration failure: {}", makerDto.getName(), closeException);
            }
            synchronized (makerTable) {
                makerTable.remove(pluginId, makerDto.getName());
            }
            makerNameRegistry.remove(makerDto.getName());
            throw e;
        }
    }

    private void startMakerThread(String makerName, Maker<?> maker) {
        if (!maker.isThread()) {
            return;
        }

        Thread makerThread = maker.getThread();
        if (makerThread == null) {
            log.warn("Maker declared thread mode but returned no thread: {}", makerName);
            return;
        }

        makerThread.start();
    }

    private void stopMakerThread(String makerName, Maker<?> maker) {
        if (!maker.isThread()) {
            return;
        }

        Thread makerThread = maker.getThread();
        if (makerThread != null) {
            makerThread.interrupt();
        } else {
            log.warn("Maker declared thread mode but returned no thread: {}", makerName);
        }
    }

    public Set<String> getMakerNames() {
        synchronized (makerTable) {
            return new HashSet<>(makerTable.columnKeySet());
        }
    }

    public void loadPlugin() {
        loadPlugin(null);
    }
    public void loadPlugin(String pluginId) {
        springPluginManager.getPlugins(PluginState.STARTED).stream()
                .filter(p -> pluginId == null || p.getPluginId().equals(pluginId))
                .forEach(pluginWrapper -> springPluginManager.getExtensions(MakerPlugin.class, pluginWrapper.getPluginId())
                        .forEach(makerPlugin -> {
                            synchronized (makerPluginTable) {
                                log.info("{}/{}", makerPlugin.getType(), makerPluginTable.contains(pluginWrapper.getPluginId(), makerPlugin.getType()));
                                if (!makerPluginTable.contains(pluginWrapper.getPluginId(), makerPlugin.getType())) {
                                    makerPluginTable.put(pluginWrapper.getPluginId(), makerPlugin.getType(), makerPlugin);
                                }
                                else {
                                    log.warn("Plugin is already loaded. id={}, type={}", pluginWrapper.getPluginId(), makerPlugin.getType());
                                }
                            }
                        }));
    }

    public ResponseEntity<Result> updateMaker(MakerDto makerDto) {
        ResponseEntity<Result> result;
        Optional<Map.Entry<String, Maker<?>>> existsMaker = getMaker(makerDto.getName());

        if (existsMaker.isPresent()) {
            existsMaker.get().getValue().update(makerDto.getArgs());
            saveToFile(getMaker(), makerStoragePath());
            result = Result.createResultSet(Result.Type.SUCCESS, "Successfully updated maker");
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR, "Update maker failed");
        }

        return result;
    }

    private String makerStoragePath() {
        return String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "makers.json");
    }
}

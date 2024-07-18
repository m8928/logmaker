package me.blueat.logmaker.core.maker;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.MakerDto;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.core.model.SenderDto;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import me.blueat.logmaker.plugin.api.exception.MakerTimeoutException;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerPlugin;
import org.pf4j.PluginState;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.xml.bind.DataBindingException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Data
public class MakerService {
    private final SpringPluginManager springPluginManager;
    private final ObjectMapper mapper;
    private Table<String, String, Maker<?>> makerTable;
    private Table<String, String, MakerPlugin> makerPluginTable;

    @PostConstruct
    protected void init() {
        makerTable = HashBasedTable.create();
        makerPluginTable = HashBasedTable.create();
        loadPlugin();
    }

    public List<MakerDto> getMaker() {
        return makerTable.cellSet().stream().map(v ->{
            MakerDto.MakerDtoBuilder makerDtoBuilder = MakerDto.builder();
                    makerDtoBuilder.name(v.getColumnKey())
                            .type(v.getValue().getType())
                            .args(v.getValue().getArgs())
                            .ref(v.getValue().getRef())
                            .size(v.getValue().getSize())
                            .regTime(v.getValue().getRegTime());

                    Object sample = null;
                    try {
                        sample = v.getValue().getData();
                    }
                    catch (MakerTimeoutException mte) {
                        log.warn("maker timeout.", mte);
                    }
                    makerDtoBuilder.sample(sample);
               return makerDtoBuilder.build();
        }).sorted(Comparator.comparing(MakerDto::getRegTime).reversed())
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
        Optional<Map.Entry<String, Maker<?>>> existsMaker = getMaker(name);

        if (existsMaker.isPresent()) {
            if (existsMaker.get().getValue().isThread()) {
                existsMaker.get().getValue().getThread().interrupt();
            }
            makerTable.remove(existsMaker.get().getKey(), name);
            return Result.createResultSet(Result.Type.SUCCESS, "Successfully deleted maker");
        }

        return Result.createResultSet(Result.Type.ERROR, "Maker does not exist");
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
        ResponseEntity<Result> result;
        Optional<Map.Entry<String, MakerPlugin>> makerPlugin = getMakerPlugin(makerDto.getType());

        if (makerPlugin.isPresent()) {
            try {
                Maker maker = makerPlugin.get().getValue().getMaker(makerDto.getName(), makerDto.getArgs());

                if (maker != null) {
                    if (addMaker(makerDto, makerPlugin.get().getKey(), maker)) {
                        result = Result.createResultSet(Result.Type.SUCCESS, "Successful maker registration");
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
        Optional<Map.Entry<String, Maker<?>>> existsMaker = getMaker(makerDto.getName());

        if (!existsMaker.isPresent()) {
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
        return new HashSet<>(makerTable.columnKeySet());
    }

    public void loadPlugin() {
        loadPlugin(null);
    }
    public void loadPlugin(String pluginId) {
        springPluginManager.getPlugins(PluginState.STARTED).stream()
                .filter(p -> pluginId == null || p.getPluginId().equals(pluginId))
                .forEach(pluginWrapper ->  springPluginManager.getExtensions(MakerPlugin.class, pluginWrapper.getPluginId())
                        .forEach(makerPlugin -> {
                            log.info("{}/{}/{}", pluginWrapper.getPluginId(), makerPlugin.getType(), getMakerPluginTable().contains(pluginWrapper.getPluginId(), makerPlugin.getType()));
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
            result = Result.createResultSet(Result.Type.SUCCESS, "Successfully updated maker");
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR, "Update maker failed");
        }

        return result;
    }
}

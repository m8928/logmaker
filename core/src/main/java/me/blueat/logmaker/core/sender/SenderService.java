package me.blueat.logmaker.core.sender;

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
import me.blueat.logmaker.core.model.SenderDto;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import me.blueat.logmaker.plugin.api.sender.Sender;
import me.blueat.logmaker.plugin.api.sender.SenderPlugin;
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
@Order(2)
public class SenderService {
    private Table<String, String, Sender<?>> senderTable;
    private Table<String, String, SenderPlugin> senderPluginTable;

    private final ObjectMapper mapper;
    private final SpringPluginManager springPluginManager;
    private final LogMakerConfig logMakerConfig;

    @PostConstruct
    protected void init() {
        senderTable = Tables.synchronizedTable(HashBasedTable.create());
        senderPluginTable = Tables.synchronizedTable(HashBasedTable.create());
        loadPlugin();
        Arrays.stream(Objects.requireNonNull(loadFromFile(String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "senders.json")
                        , SenderDto[].class)))
                .forEach(senderDto -> createSender(senderDto, true));
        log.info("Initialized Sender Service");
    }

    public Optional<Map.Entry<String, Sender<?>>> getSender(String name) {
        return senderTable.column(name).entrySet().stream().findFirst();
    }

    public Optional<Map.Entry<String, SenderPlugin>> getSenderPlugin(String name) {
        if (senderPluginTable.containsColumn(name)) {
            return senderPluginTable.column(name).entrySet().stream().findFirst();
        }
        else {
            return Optional.empty();
        }
    }

    public List<SenderDto> getSender() {
        return senderTable.cellSet().stream().map(v ->
                SenderDto.builder()
                        .name(v.getValue().getSenderName())
                        .type(v.getValue().getType())
                        .args(v.getValue().getArgs())
                        .ref(v.getValue().getRef())
                        .count(v.getValue().getCount())
                        .bytes(v.getValue().getBytes())
                        .bytesPerSec(v.getValue().getBytesPerSec())
                        .regTime(v.getValue().getRegTime())
                        .build())
                .sorted(Comparator.comparing(SenderDto::getRegTime).reversed()).collect(Collectors.toList());
    }

    public ResponseEntity<Result> deleteSender(String name) {
        Optional<Map.Entry<String, Sender<?>>> existsSender = getSender(name);

        if (existsSender.isPresent()) {
            if (existsSender.get().getValue().isThread()) {
                existsSender.get().getValue().getThread().interrupt();
            }
            senderTable.remove(existsSender.get().getKey(), name);
            saveToFile(getSender(), String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "senders.json"));
            return Result.createResultSet(Result.Type.SUCCESS, "Successfully deleted sender");
        }

        return Result.createResultSet(Result.Type.ERROR, "Sender does not exist");
    }

    public List<ResponseEntity<Result>> importSender(MultipartFile json) {
        try {
            SenderDto[] logs = mapper.readValue(json.getBytes(), SenderDto[].class);
            return Arrays.stream(logs).map(this::createSender).collect(Collectors.toList());
        }
        catch (IOException | DataBindingException e) {
            return Lists.newArrayList(Result.createResultSet(Result.Type.ERROR, "Sender file import failed"));
        }
    }

    public ResponseEntity<Result> createSender(SenderDto senderDto) {
        return createSender(senderDto, false);
    }

    public ResponseEntity<Result> createSender(SenderDto senderDto, boolean isImport) {
        ResponseEntity<Result> result;
        Optional<Map.Entry<String, SenderPlugin>> senderPlugin = getSenderPlugin(senderDto.getType());

        if (senderPlugin.isPresent()) {
            try {
                Sender sender = senderPlugin.get().getValue().getSender(senderDto.getName(), senderDto.getArgs());

                if (sender != null) {
                    if (addSender(senderDto, senderPlugin.get().getKey(), sender)) {
                        result = Result.createResultSet(Result.Type.SUCCESS, "Successful sender registration");

                        if (!isImport) {
                            saveToFile(getSender(), String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "senders.json"));
                        }
                    }
                    else {
                        result = Result.createResultSet(Result.Type.ERROR, String.format("%s is the sender name already in use", senderDto.getName()));
                    }
                }
                else {
                    result = Result.createResultSet(Result.Type.ERROR, String.format("%s is an unavailable sender type", senderDto.getType()));
                }
            }
            catch (ArgumentsNotValidException anve) {
                result = Result.createResultSet(Result.Type.ERROR, String.format("Invalid sender argument (%s)", senderDto.getArgs()));
            }
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR, String.format("%s is an unavailable sender type", senderDto.getType()));
        }

        return result;
    }

    public boolean addSender(SenderDto senderDto, String pluginId, Sender sender) {
        if (getSender(senderDto.getName()).isEmpty()) {
            senderTable.put(pluginId, senderDto.getName(), sender);
            if (sender.isThread()) {
                sender.getThread().start();
            }
            return true;
        }
        else {
            return false;
        }
    }

    public Set<String> getSenderNames() {
        return new HashSet<>(senderTable.columnKeySet());
    }

    public void loadPlugin() {
        loadPlugin(null);
    }

    public void loadPlugin(String pluginId) {
        springPluginManager.getPlugins(PluginState.STARTED).stream()
                .filter(p -> pluginId == null || p.getPluginId().equals(pluginId))
                .forEach(pluginWrapper -> springPluginManager.getExtensions(SenderPlugin.class, pluginWrapper.getPluginId())
                        .forEach(senderPlugin -> {
                            log.info("{}", senderPlugin.getType());
                            if (!getSenderPluginTable().contains(pluginWrapper.getPluginId(), senderPlugin.getType())) {
                                getSenderPluginTable().put(pluginWrapper.getPluginId(), senderPlugin.getType(), senderPlugin);
                            }
                            else {
                                log.warn("Plugin is already loaded. id={}, type={}", pluginWrapper.getPluginId(), senderPlugin.getType());
                            }
                        }));
    }

    public ResponseEntity<Result> updateSender(SenderDto senderDto) {
        ResponseEntity<Result> result;
        Optional<Map.Entry<String, Sender<?>>> existsSender = getSender(senderDto.getName());

        if (existsSender.isPresent()) {
            existsSender.get().getValue().update(senderDto.getArgs());
            saveToFile(getSender(), String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "senders.json"));
            result = Result.createResultSet(Result.Type.SUCCESS, "Successfully updated sender");
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR, "Update sender failed");
        }

        return result;
    }
}

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
        SenderDto[] loadedSenders = loadFromFile(senderStoragePath(), SenderDto[].class);
        if (loadedSenders != null) {
            Arrays.stream(loadedSenders).forEach(senderDto -> createSender(senderDto, true));
        }
        log.info("Initialized Sender Service");
    }

    public Optional<Map.Entry<String, Sender<?>>> getSender(String name) {
        synchronized (senderTable) {
            return senderTable.column(name).entrySet().stream()
                    .findFirst()
                    .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
        }
    }

    public Optional<Map.Entry<String, SenderPlugin>> getSenderPlugin(String name) {
        synchronized (senderPluginTable) {
            if (senderPluginTable.containsColumn(name)) {
                return senderPluginTable.column(name).entrySet().stream()
                        .findFirst()
                        .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            }
            else {
                return Optional.empty();
            }
        }
    }

    public List<SenderDto> getSender() {
        List<Sender<?>> snapshot;
        synchronized (senderTable) {
            snapshot = senderTable.cellSet().stream()
                    .map(Table.Cell::getValue)
                    .toList();
        }

        return snapshot.stream().map(v ->
                SenderDto.builder()
                        .name(v.getSenderName())
                        .type(v.getType())
                        .args(v.getArgs())
                        .ref(v.getRef())
                        .count(v.getCount())
                        .bytes(v.getBytes())
                        .bytesPerSec(v.getBytesPerSec())
                        .limit(v.getLimit())
                        .regTime(v.getRegTime())
                        .build())
                .sorted(Comparator.comparing(SenderDto::getRegTime).reversed()).collect(Collectors.toList());
    }

    public ResponseEntity<Result> deleteSender(String name) {
        synchronized (senderTable) {
            Optional<Map.Entry<String, Sender<?>>> existsSender = senderTable.column(name).entrySet().stream()
                    .findFirst()
                    .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            if (existsSender.isEmpty()) {
                return Result.createResultSet(Result.Type.ERROR, "Sender does not exist");
            }
            Sender<?> sender = existsSender.get().getValue();
            stopSenderThread(name, sender);
            try {
                sender.close();
            } catch (Exception e) {
                log.warn("Failed to close sender: {}", name, e);
            } finally {
                senderTable.remove(existsSender.get().getKey(), name);
            }
        }
        saveToFile(getSender(), senderStoragePath());
        return Result.createResultSet(Result.Type.SUCCESS, "Successfully deleted sender");
    }

    public void deleteSendersByPlugin(String pluginId) {
        List<String> senderNames;
        synchronized (senderTable) {
            senderNames = new ArrayList<>(senderTable.row(pluginId).keySet());
        }
        senderNames.forEach(this::deleteSender);
    }

    public boolean hasReferencedSendersByPlugin(String pluginId) {
        synchronized (senderTable) {
            return senderTable.row(pluginId).values().stream()
                    .anyMatch(sender -> sender.getRef() > 0);
        }
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
        Optional<Map.Entry<String, SenderPlugin>> senderPlugin = getSenderPlugin(senderDto.getType());

        if (senderPlugin.isEmpty()) {
            return unavailableSenderType(senderDto);
        }

        try {
            return createSender(senderDto, isImport, senderPlugin.get());
        }
        catch (ArgumentsNotValidException anve) {
            return Result.createResultSet(Result.Type.ERROR, String.format("Invalid sender argument (%s)", senderDto.getArgs()));
        }
    }

    private ResponseEntity<Result> createSender(SenderDto senderDto, boolean isImport,
                                                Map.Entry<String, SenderPlugin> senderPlugin)
            throws ArgumentsNotValidException {
        Sender<?> sender = senderPlugin.getValue().getSender(senderDto.getName(), senderDto.getArgs());

        if (sender == null) {
            return unavailableSenderType(senderDto);
        }

        applyLimit(senderDto, sender);
        if (!addSender(senderDto, senderPlugin.getKey(), sender)) {
            return Result.createResultSet(Result.Type.ERROR,
                    String.format("%s is the sender name already in use", senderDto.getName()));
        }

        if (!isImport) {
            saveToFile(getSender(), senderStoragePath());
        }
        return Result.createResultSet(Result.Type.SUCCESS, "Successful sender registration");
    }

    private void applyLimit(SenderDto senderDto, Sender<?> sender) {
        if (senderDto.getLimit() != null && senderDto.getLimit() > 0) {
            sender.setLimit(senderDto.getLimit());
        }
    }

    private ResponseEntity<Result> unavailableSenderType(SenderDto senderDto) {
        return Result.createResultSet(Result.Type.ERROR,
                String.format("%s is an unavailable sender type", senderDto.getType()));
    }

    public boolean addSender(SenderDto senderDto, String pluginId, Sender<?> sender) {
        synchronized (senderTable) {
            if (senderTable.containsColumn(senderDto.getName())) {
                try {
                    sender.close();
                } catch (Exception e) {
                    log.warn("Failed to close sender after duplicate name check: {}", senderDto.getName(), e);
                }
                return false;
            }
            try {
                senderTable.put(pluginId, senderDto.getName(), sender);
                startSenderThread(senderDto.getName(), sender);
                return true;
            } catch (Exception e) {
                try {
                    sender.close();
                } catch (Exception closeException) {
                    log.warn("Failed to close sender after registration failure: {}", senderDto.getName(), closeException);
                }
                senderTable.remove(pluginId, senderDto.getName());
                throw e;
            }
        }
    }

    private void startSenderThread(String senderName, Sender<?> sender) {
        if (!sender.isThread()) {
            return;
        }

        Thread senderThread = sender.getThread();
        if (senderThread == null) {
            log.warn("Sender declared thread mode but returned no thread: {}", senderName);
            return;
        }

        senderThread.start();
    }

    private void stopSenderThread(String senderName, Sender<?> sender) {
        if (!sender.isThread()) {
            return;
        }

        Thread senderThread = sender.getThread();
        if (senderThread != null) {
            senderThread.interrupt();
        } else {
            log.warn("Sender declared thread mode but returned no thread: {}", senderName);
        }
    }

    public Set<String> getSenderNames() {
        synchronized (senderTable) {
            return new HashSet<>(senderTable.columnKeySet());
        }
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
                            synchronized (senderPluginTable) {
                                if (!senderPluginTable.contains(pluginWrapper.getPluginId(), senderPlugin.getType())) {
                                    senderPluginTable.put(pluginWrapper.getPluginId(), senderPlugin.getType(), senderPlugin);
                                }
                                else {
                                    log.warn("Plugin is already loaded. id={}, type={}", pluginWrapper.getPluginId(), senderPlugin.getType());
                                }
                            }
                        }));
    }

    public ResponseEntity<Result> updateSender(SenderDto senderDto) {
        ResponseEntity<Result> result;
        Optional<Map.Entry<String, Sender<?>>> existsSender = getSender(senderDto.getName());

        if (existsSender.isPresent()) {
            existsSender.get().getValue().update(senderDto.getArgs());
            saveToFile(getSender(), senderStoragePath());
            result = Result.createResultSet(Result.Type.SUCCESS, "Successfully updated sender");
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR, "Update sender failed");
        }

        return result;
    }

    private String senderStoragePath() {
        return String.format("%s%s%s", logMakerConfig.getDataRootPath(), File.separator, "senders.json");
    }
}

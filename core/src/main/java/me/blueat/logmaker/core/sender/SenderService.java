package me.blueat.logmaker.core.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.LogDto;
import me.blueat.logmaker.core.model.SenderDto;
import me.blueat.logmaker.core.model.Result;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import me.blueat.logmaker.plugin.api.sender.Sender;
import me.blueat.logmaker.plugin.api.sender.SenderPlugin;
import org.pf4j.PluginState;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.xml.bind.DataBindingException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Data
public class SenderService {
    private Table<String, String, Sender<?>> senderTable;
    private Table<String, String, SenderPlugin> senderPluginTable;

    private final ObjectMapper mapper;
    private final SpringPluginManager springPluginManager;

    @PostConstruct
    protected void init() {
        senderTable = HashBasedTable.create();
        senderPluginTable = HashBasedTable.create();
        loadPlugin();
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
        List<SenderDto> result = senderTable.cellSet().stream().map(v ->
                        SenderDto.builder()
                                .name(v.getValue().getSenderName())
                                .type(v.getValue().getType())
                                .args(v.getValue().getArgs())
                                .ref(v.getValue().getRef())
                                .count(v.getValue().getCount())
                                .regTime(v.getValue().getRegTime())
                                .build())
                .collect(Collectors.toList());
        result.sort(Comparator.comparing(SenderDto::getRegTime).reversed());
        return result;
    }

    public ResponseEntity<Result> deleteSender(String name) {
        Optional<Map.Entry<String, Sender<?>>> existsSender = getSender(name);

        if (existsSender.isPresent()) {
            if (existsSender.get().getValue().isThread()) {
                existsSender.get().getValue().getThread().interrupt();
            }
            senderTable.remove(existsSender.get().getKey(), name);
            return Result.createResultSet(Result.Type.SUCCESS, "Successfully deleted sender");
        }

        return Result.createResultSet(Result.Type.ERROR, "Sender does not exist");
    }

    public List<ResponseEntity<Result>> importSender(MultipartFile json) {
        try {
            SenderDto[] logs = mapper.readValue(json.getBytes(), SenderDto[].class);
            return Arrays.stream(logs).map(dto -> createSender(dto)).collect(Collectors.toList());
        }
        catch (IOException | DataBindingException e) {
            return Lists.newArrayList(Result.createResultSet(Result.Type.ERROR, "Sender file import failed"));
        }
    }

    public ResponseEntity<Result> createSender(SenderDto senderDto) {
        ResponseEntity<Result> result;
        Optional<Map.Entry<String, SenderPlugin>> senderPlugin = getSenderPlugin(senderDto.getType());

        if (senderPlugin.isPresent()) {
            try {
                Sender sender = senderPlugin.get().getValue().getSender(senderDto.getName(), senderDto.getArgs());

                if (sender != null) {
                    if (addSender(senderDto, senderPlugin.get().getKey(), sender)) {
                        return Result.createResultSet(Result.Type.SUCCESS, "Successful sender registration");
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
        Optional<Map.Entry<String, Sender<?>>> existsSender = getSender(senderDto.getName());

        if (!existsSender.isPresent()) {
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
        Set<String> senderNames = new HashSet<>();
        senderTable.columnKeySet().forEach((k) -> senderNames.add(k));
        return senderNames;
    }

    public void loadPlugin() {
        loadPlugin(null);
    }
    public void loadPlugin(String pluginId) {
        springPluginManager.getPlugins(PluginState.STARTED).stream()
                .filter(p -> (pluginId == null) || (pluginId != null && p.getPluginId().equals(pluginId)))
                .forEach(pluginWrapper -> springPluginManager.getExtensions(SenderPlugin.class)
                        .forEach(senderPlugin -> {
                            log.info("{}", senderPlugin.getType());
                            getSenderPluginTable().put(pluginWrapper.getPluginId(), senderPlugin.getType(), senderPlugin);
                        }));
    }

    public ResponseEntity<Result> updateSender(SenderDto senderDto) {
        ResponseEntity<Result> result;
        Optional<Map.Entry<String, Sender<?>>> existsSender = getSender(senderDto.getName());

        if (existsSender.isPresent()) {
            existsSender.get().getValue().update(senderDto.getArgs());
            result = Result.createResultSet(Result.Type.SUCCESS, "Successfully updated sender");
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR, "Update sender failed");
        }

        return result;
    }

}

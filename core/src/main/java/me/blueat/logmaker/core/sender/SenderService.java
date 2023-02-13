package me.blueat.logmaker.core.sender;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.SenderDto;
import me.blueat.logmaker.core.util.Result;
import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import me.blueat.logmaker.plugin.api.sender.Sender;
import me.blueat.logmaker.plugin.api.sender.SenderPlugin;
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
public class SenderService {
    private Table<String, String, Sender<?>> senderTable;
    private Table<String, String, SenderPlugin> senderPluginTable;

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
        return senderPluginTable.column(name).entrySet().stream().findFirst();
    }

    public List<SenderDto> getSender() {
        return senderTable.cellSet().stream().map(v ->
                SenderDto.builder()
                        .name(v.getValue().getSenderName())
                        .type(v.getValue().getType())
                        .args(v.getValue().getArgs())
                        .ref(v.getValue().getRef())
                        .count(v.getValue().getCount()).build())
                .collect(Collectors.toList());
    }

    public Result deleteSender(String name) {
        Optional<Map.Entry<String, Sender<?>>> existsSender = getSender(name);

        if (existsSender.isPresent()) {
            if (existsSender.get().getValue().isThread()) {
                existsSender.get().getValue().getThread().interrupt();
            }
            senderTable.remove(existsSender.get().getKey(), name);
            return Result.createResultSet(Result.Type.SUCCESS);
        }

        return Result.createResultSet(Result.Type.ERROR);
    }

    public Result createSender(SenderDto senderDto) {
        Result result;
        Optional<Map.Entry<String, SenderPlugin>> senderPlugin = getSenderPlugin(senderDto.getType());

        if (senderPlugin.isPresent()) {
            try {
                Sender sender = senderPlugin.get().getValue().getSender(senderDto.getName(), senderDto.getArgs());

                if (sender != null) {
                    if (addSender(senderDto, senderPlugin.get().getKey(), sender)) {
                        result = Result.createResultSet(Result.Type.SUCCESS);
                    }
                    else {
                        result = Result.createResultSet(Result.Type.ERROR, String.format("%s is already used", senderDto.getName()));
                    }
                }
                else {
                    result = Result.createResultSet(Result.Type.ERROR, String.format("%s is invalid", senderDto.getName()));
                }
            }
            catch (ArgumentsNotValidException anve) {
                result = Result.createResultSet(Result.Type.ERROR, String.format("%s is invalid", senderDto.getName()));
            }
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR, String.format("%s is not support", senderDto.getType()));
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

    public Result updateSender(SenderDto senderDto) {
        Result result;
        Optional<Map.Entry<String, Sender<?>>> existsSender = getSender(senderDto.getName());

        if (existsSender.isPresent()) {
            existsSender.get().getValue().update(senderDto.getArgs());
            result = Result.createResultSet(Result.Type.SUCCESS);
        }
        else {
            result = Result.createResultSet(Result.Type.ERROR);
        }

        return result;
    }

}

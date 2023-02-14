package me.blueat.logmaker.core.plugin;

import com.google.common.collect.Maps;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.config.PluginConfig;
import me.blueat.logmaker.core.maker.MakerService;
import me.blueat.logmaker.core.model.MakerDto;
import me.blueat.logmaker.core.model.PluginDto;
import me.blueat.logmaker.core.model.SenderDto;
import me.blueat.logmaker.core.sender.SenderService;
import me.blueat.logmaker.core.model.Result;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Data
public class PluginService {
    private final PluginConfig pluginConfig;
    private final MakerService makerService;
    private final SenderService senderService;
    private final SpringPluginManager springPluginManager;

    @PostConstruct
    protected void init() {
    }

    public ResponseEntity<Result> uploadPlugin(MultipartFile file) {
        Path pluginPath = null;
        try {
            pluginPath = Paths.get(pluginConfig.pluginManager().getPluginsRoot().toString(),file.getOriginalFilename());
            file.transferTo(pluginPath);

            String pluginId = springPluginManager.loadPlugin(pluginPath);
            springPluginManager.startPlugin(pluginId);
            makerService.loadPlugin(pluginId);
            senderService.loadPlugin(pluginId);

            return Result.createResultSet(Result.Type.SUCCESS, "Plugin uploaded successfully");
        }
        catch (Exception e) {
            if (pluginPath != null) {
                try {
                    Files.delete(pluginPath);
                }
                catch (IOException ioe) {
                 //NOTHING
                }
            }
            return Result.createResultSet(Result.Type.ERROR, String.format("Plugin upload failed (%s)", e.getMessage()));
        }
    }

    public int getRefPlugin(String pluginId) {
        return makerService.getMakerTable().row(pluginId).values().size() +
                senderService.getSenderTable().row(pluginId).values().size();
    }

    public ResponseEntity<Result> deletePlugin(String pluginId) {
        ResponseEntity<Result> result;
        try {
            makerService.getMakerPluginTable().row(pluginId).clear();
            senderService.getSenderPluginTable().row(pluginId).clear();
            springPluginManager.stopPlugin(pluginId);
            springPluginManager.deletePlugin(pluginId);
            result = Result.createResultSet(Result.Type.SUCCESS, "Successfully deleted plugin");
        }
        catch (Exception e) {
            result = Result.createResultSet(Result.Type.ERROR, String.format("Plugin deletion failed (%s)", e.getMessage()));
        }

        return result;
    }

    public List<PluginDto> getPlugin() {
        List<PluginDto> result = new ArrayList<>();
        springPluginManager.getPlugins().forEach(pluginWrapper -> {
            result.add(PluginDto.builder()
                    .name(pluginWrapper.getPluginId())
                    .version(pluginWrapper.getDescriptor().getVersion())
                    .provider(pluginWrapper.getDescriptor().getProvider())
                    .filename(pluginWrapper.getPluginPath().getFileName().toString())
                    .ref(getRefPlugin(pluginWrapper.getPluginId()))
                    .build());
        });
        return result;
    }

    public List<SenderDto> getSender() {
        List<SenderDto> result = new ArrayList<>();

        senderService.getSenderPluginTable().values().forEach((v) -> {
            SenderDto.SenderDtoBuilder makerDtoBuilder = SenderDto.builder().type(v.getType());
            makerDtoBuilder.args(Maps.newLinkedHashMap(v.getSenderArgsMap()));
            result.add(makerDtoBuilder.build());
        });

        return result;
    }

    public List<MakerDto> getMaker() {
        List<MakerDto> result = new ArrayList<>();
        makerService.getMakerPluginTable().columnMap().values().forEach((v) ->
                v.values().stream().forEach(iv -> {
                    MakerDto.MakerDtoBuilder makerDtoBuilder = MakerDto.builder().type(iv.getType());
                    makerDtoBuilder.args(Maps.newLinkedHashMap(iv.getMakerArgsMap()));
                    result.add(makerDtoBuilder.build());
                }));

        return result;
    }
}

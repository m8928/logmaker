package me.blueat.logmaker.core.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.plugin.api.sender.Sender;
import me.blueat.logmaker.plugin.api.sender.SenderPlugin;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SenderService {
    private HashMap<String, Sender<?>> senderMap;
    private HashMap<String, SenderPlugin> senderPluginMap;

    private final SpringPluginManager springPluginManager;

    @PostConstruct
    protected void init() {
        senderMap = new HashMap<>();
        senderPluginMap = new HashMap<>();

        springPluginManager.getExtensions(SenderPlugin.class)
                .forEach(senderPlugin -> senderPluginMap.put(senderPlugin.getType(), senderPlugin));

        log.info("{}", springPluginManager.getExtensions(SenderPlugin.class));
    }

    public List<SenderDto> getSender() {
        return senderMap.values().stream().map(v -> SenderDto.builder().name(v.getSenderName()).type(v.getType()).args(new HashMap<>()).build()).collect(Collectors.toList());
    }

    public boolean removeSender(String name) {
        Optional<Sender> optionalSender = Optional.ofNullable(senderMap.get(name));

        if (optionalSender.isPresent()) {
            if (optionalSender.get().isThread()) {
                ((Thread)senderMap.get(optionalSender.get())).interrupt();
            }
            senderMap.remove(name);
            return true;
        }

        return false;
    }

    public boolean addSender(SenderDto senderDto, Sender sender) {
        if (!senderMap.containsKey(senderDto.getName())) {
            senderMap.put(senderDto.getName(), sender);
            if (sender.isThread()) {
                ((Thread)sender).start();
            }
            return true;
        }
        else {
            return false;
        }
    }

    public Sender<?> getSender(String name) {
        return senderMap.get(name);
    }

    public HashMap<String, SenderPlugin> getSenderPluginMap() {
        return senderPluginMap;
    }
}

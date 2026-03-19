package me.blueat.logmaker.core.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@Getter
public class LogMakerConfig {
    @Value("${data.root:}")
    private String dataRoot;
    private Path dataRootPath;

    @PostConstruct
    public void init() {
        if (dataRoot.isEmpty()) {
            dataRootPath = Paths.get(System.getProperty("user.home"), ".logmaker-data");
        }
        else {
            dataRootPath = Paths.get(dataRoot);
        }
    }
}

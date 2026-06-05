package me.blueat.logmaker.core.config;

import lombok.extern.slf4j.Slf4j;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@Slf4j
public class PluginConfig {

    @Value("${plugin.root:}")
    private String pluginRoot;

    @Bean
    public SpringPluginManager pluginManager() {
        Path pluginRootPath;

        if (pluginRoot.isEmpty()) {
            pluginRootPath = Paths.get(System.getProperty("user.home"), ".logmaker-plugin");
        }
        else {
            pluginRootPath = Paths.get(pluginRoot);
        }

        try {
            Files.createDirectories(pluginRootPath);
        }
        catch (IOException ioe) {
            log.error("Failed to create plugin directory", ioe);
        }

        return new SpringPluginManager(pluginRootPath);
    }
}

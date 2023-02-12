package me.blueat.logmaker.core.config;

import org.pf4j.spring.SpringPluginManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class PluginConfig {

    @Value("${plugin.root:}")
    private String pluginRoot;

    @Bean
    public SpringPluginManager pluginManager() {
        Path pluginRootPath;

        if (pluginRoot.isEmpty()) {
            pluginRootPath = Path.of(System.getProperty("user.home"), ".logmaker-plugin");
        }
        else {
            pluginRootPath = Path.of(pluginRoot);
        }

        try {
            Files.createDirectories(pluginRootPath);
        }
        catch (IOException ioe) {}

        return new SpringPluginManager(pluginRootPath);
    }
}

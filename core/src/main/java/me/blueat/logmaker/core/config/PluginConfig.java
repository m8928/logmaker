package me.blueat.logmaker.core.config;

import org.pf4j.spring.SpringPluginManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class PluginConfig {

    @Value("${plugin.root:~/.plugin}")
    private String pluginRoot;

    @Bean
    public SpringPluginManager pluginManager() {
        return new SpringPluginManager(Path.of(pluginRoot));
    }
}

package me.blueat.logmaker.core.config;

import org.pf4j.spring.SpringPluginManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PluginConfig {
    @Bean
    public SpringPluginManager pluginManager() {
        return new SpringPluginManager();
    }
}

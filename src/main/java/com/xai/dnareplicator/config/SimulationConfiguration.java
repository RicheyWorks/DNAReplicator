package com.xai.dnareplicator.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

@Configuration
@EnableConfigurationProperties(SimulationProperties.class)
public class SimulationConfiguration {

    @Bean
    public Random simulationRandom() {
        return new Random();
    }

    @Bean
    public ConfigBridge configBridge(SimulationProperties properties, Random simulationRandom) {
        return new ConfigBridge(properties, simulationRandom);
    }

    /**
     * Initializes deprecated static {@link Config} accessors from bound properties at startup.
     */
    public static final class ConfigBridge {
        public ConfigBridge(SimulationProperties properties, Random simulationRandom) {
            Config.initialize(properties, simulationRandom);
        }
    }
}

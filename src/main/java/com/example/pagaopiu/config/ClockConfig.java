package com.example.pagaopiu.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    public static final ZoneId ZONE_LIMA = ZoneId.of("America/Lima");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE_LIMA);
    }
}

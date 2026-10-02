package com.knowlink.api.config;

import com.knowlink.api.shared.utils.AppTimeZone;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfiguration {

    @Bean
    public Clock applicationClock() {
        return Clock.system(AppTimeZone.ZONE);
    }
}
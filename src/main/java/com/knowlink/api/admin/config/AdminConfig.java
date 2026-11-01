package com.knowlink.api.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminSeedProperties.class)
public class AdminConfig {
}
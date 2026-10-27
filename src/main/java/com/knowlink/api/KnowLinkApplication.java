package com.knowlink.api;

import com.knowlink.api.support.config.SupportProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(SupportProperties.class)
public class KnowLinkApplication {

	public static void main(String[] args) {
		SpringApplication.run(KnowLinkApplication.class, args);
	}
}

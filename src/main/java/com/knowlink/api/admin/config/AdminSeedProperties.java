package com.knowlink.api.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "knowlink.admin")
public class AdminSeedProperties {

    private List<AdminAccount> accounts = new ArrayList<>();

    @Getter
    @Setter
    public static class AdminAccount {
        private String email;
        private String password;
        private String fullName;
    }
}
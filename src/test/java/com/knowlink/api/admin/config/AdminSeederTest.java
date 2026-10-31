package com.knowlink.api.admin.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    @Mock
    private IUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminSeedProperties properties;
    private AdminSeeder seeder;

    @BeforeEach
    void setUp() {
        properties = new AdminSeedProperties();
        seeder = new AdminSeeder(properties, userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Crea la cuenta ADMIN configurada y normaliza los espacios")
    void run_createsConfiguredAdmin() {
        properties.setAccounts(List.of(account(" admin@test.com ", "secret-password", " Admin KnowLink ")));
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret-password")).thenReturn("encoded-password");

        seeder.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@test.com");
        assertThat(saved.getFullName()).isEqualTo("Admin KnowLink");
        assertThat(saved.getPassword()).isEqualTo("encoded-password");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Es idempotente: no duplica ni modifica una cuenta existente")
    void run_withExistingEmail_doesNotSave() {
        properties.setAccounts(List.of(account("admin@test.com", "secret-password", "Admin")));
        when(userRepository.findByEmail("admin@test.com"))
                .thenReturn(Optional.of(User.builder().email("admin@test.com").build()));

        seeder.run();

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Sin configuración no crea cuentas")
    void run_withoutConfiguration_doesNothing() {
        seeder.run();

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Ignora cuentas incompletas")
    void run_withIncompleteAccount_doesNotSave() {
        properties.setAccounts(List.of(account("admin@test.com", " ", "Admin")));

        seeder.run();

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Nunca registra la contraseña en los logs")
    void run_neverLogsPassword() {
        Logger logger = (Logger) LoggerFactory.getLogger(AdminSeeder.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        properties.setAccounts(List.of(account("admin@test.com", "super-secret-password", "Admin")));
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("super-secret-password")).thenReturn("encoded");

        try {
            seeder.run();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .noneMatch(event -> event.getFormattedMessage().contains("super-secret-password"));
    }

    private AdminSeedProperties.AdminAccount account(String email, String password, String fullName) {
        AdminSeedProperties.AdminAccount account = new AdminSeedProperties.AdminAccount();
        account.setEmail(email);
        account.setPassword(password);
        account.setFullName(fullName);
        return account;
    }
}

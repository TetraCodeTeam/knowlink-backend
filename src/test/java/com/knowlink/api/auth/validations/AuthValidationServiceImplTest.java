package com.knowlink.api.auth.validations;

import com.knowlink.api.exceptions.custom_exceptions.AccountDeactivatedException;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IUserProfileLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthValidationServiceImplTest {

    private static final String RAW_PASSWORD = "Password123!";
    private static final String ENCODED_PASSWORD = "encoded";

    @Mock
    private IUserProfileLookupService userProfileLookupService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthValidationServiceImpl validationService;

    @BeforeEach
    void setUp() {
        validationService = new AuthValidationServiceImpl(userProfileLookupService, passwordEncoder);
    }

    private User user(AccountStatus status) {
        return User.builder()
                .userId(UUID.randomUUID())
                .email("user@test.com")
                .password(ENCODED_PASSWORD)
                .role(Role.STUDENT)
                .accountStatus(status)
                .build();
    }

    @Test
    @DisplayName("does nothing for an account that is not DEACTIVATED")
    void ifDeactivated_notDeactivated_doesNothing() {
        User active = user(AccountStatus.ACTIVE);

        assertThatCode(() -> validationService
                .ifDeactivatedAccountThrowExceptionOrBadCredentials(active, RAW_PASSWORD))
                .doesNotThrowAnyException();

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("throws AccountDeactivatedException when DEACTIVATED and the password is correct")
    void ifDeactivated_correctPassword_throwsAccountDeactivated() {
        User deactivated = user(AccountStatus.DEACTIVATED);
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> validationService
                .ifDeactivatedAccountThrowExceptionOrBadCredentials(deactivated, RAW_PASSWORD))
                .isInstanceOf(AccountDeactivatedException.class);
    }

    @Test
    @DisplayName("throws BadCredentialsException when DEACTIVATED and the password is wrong")
    void ifDeactivated_wrongPassword_throwsBadCredentials() {
        User deactivated = user(AccountStatus.DEACTIVATED);
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> validationService
                .ifDeactivatedAccountThrowExceptionOrBadCredentials(deactivated, RAW_PASSWORD))
                .isInstanceOf(BadCredentialsException.class);
    }
}
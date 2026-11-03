package com.knowlink.api.users.services;

import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.mappers.UserMapper;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.implementations.UserServiceImpl;
import com.knowlink.api.users.services.interfaces.ITokenService;
import com.knowlink.api.users.utils.Constants;
import com.knowlink.api.users.validation.IUserValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private IUserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ITokenService tokenService;
    @Mock
    private IUserValidationService userValidationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private UserServiceImpl userService;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, userMapper, passwordEncoder, tokenService,
                userValidationService, eventPublisher, FIXED_CLOCK);
        userId = UUID.randomUUID();
        user = User.builder()
                .userId(userId)
                .fullName("Tutor Test")
                .email("tutor@test.com")
                .password("encoded")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("deactivateAccount marks an active account as DEACTIVATED and stores deactivatedAt")
    void deactivateAccount_activeWithoutBookings_deactivates() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        userService.deactivateAccount(userId);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        assertThat(user.getDeactivatedAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
        verify(userValidationService).ifUserHasActiveBookingsThrowException(userId);
        verify(userValidationService).ifUserHasActiveClaimsOrRetainedFundsThrowException(userId);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("deactivateAccount does not change the account when a claim or retained funds exist")
    void deactivateAccount_activeClaimOrRetainedFunds_throwsConflictAndKeepsAccount() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        doThrow(new BusinessConflictException(
                Constants.ACCOUNT_HAS_ACTIVE_CLAIMS_CODE,
                Constants.ACCOUNT_HAS_ACTIVE_CLAIMS_MESSAGE,
                "user has active claims"))
                .when(userValidationService).ifUserHasActiveClaimsOrRetainedFundsThrowException(userId);

        assertThatThrownBy(() -> userService.deactivateAccount(userId))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_HAS_ACTIVE_CLAIMS"));

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getDeactivatedAt()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivateAccount does not change the account when an active booking or hold exists")
    void deactivateAccount_activeBookingOrHold_throwsConflictAndKeepsAccount() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        doThrow(new BusinessConflictException(
                Constants.ACCOUNT_HAS_ACTIVE_BOOKINGS_CODE,
                Constants.ACCOUNT_HAS_ACTIVE_BOOKINGS_MESSAGE,
                "user has active bookings or holds"))
                .when(userValidationService).ifUserHasActiveBookingsThrowException(userId);

        assertThatThrownBy(() -> userService.deactivateAccount(userId))
                .isInstanceOfSatisfying(BusinessConflictException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_HAS_ACTIVE_BOOKINGS");
                    assertThat(ex.getUserMessage())
                            .isEqualTo("No pod\u00e9s dar de baja tu cuenta con reservas activas pendientes");
                });

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getDeactivatedAt()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivateAccount with only completed, cancelled or expired bookings deactivates the account")
    void deactivateAccount_onlyFinishedBookings_deactivates() {
        // The validation service returns normally when no PENDING/BOOKED/IN_PROGRESS booking or hold exists.
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        userService.deactivateAccount(userId);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("deactivateAccount of an INACTIVE account throws ACCOUNT_NOT_ACTIVE")
    void deactivateAccount_inactive_throwsAccountNotActive() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        user.setAccountStatus(AccountStatus.INACTIVE);

        assertThatThrownBy(() -> userService.deactivateAccount(userId))
                .isInstanceOfSatisfying(BusinessConflictException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_NOT_ACTIVE");
                    assertThat(ex.getUserMessage()).isEqualTo("Tu cuenta no est\u00e1 activa.");
                });

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(user.getDeactivatedAt()).isNull();
        verifyNoInteractions(userValidationService);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivateAccount of an already DEACTIVATED account throws ACCOUNT_NOT_ACTIVE")
    void deactivateAccount_alreadyDeactivated_throwsAccountNotActive() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        user.setAccountStatus(AccountStatus.DEACTIVATED);
        user.setDeactivatedAt(LocalDateTime.now(FIXED_CLOCK).minusDays(1));

        assertThatThrownBy(() -> userService.deactivateAccount(userId))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_NOT_ACTIVE"));

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        verifyNoInteractions(userValidationService);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("findVisibleByIdOrThrowException hides a DEACTIVATED user from another student")
    void findVisibleByIdOrThrowException_deactivatedTarget_throwsNotFound() {
        when(userRepository.findByUserIdAndAccountStatusNot(userId, AccountStatus.DELETED))
                .thenReturn(Optional.of(user));
        user.setAccountStatus(AccountStatus.DEACTIVATED);
        User requester = User.builder()
                .userId(UUID.randomUUID())
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> userService.findVisibleByIdOrThrowException(userId,
                new UserPrincipal(requester)))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("findVisibleByIdOrThrowException lets the owner read their own deactivated account")
    void findVisibleByIdOrThrowException_self_returnsUser() {
        when(userRepository.findByUserIdAndAccountStatusNot(userId, AccountStatus.DELETED))
                .thenReturn(Optional.of(user));
        user.setAccountStatus(AccountStatus.DEACTIVATED);

        User result = userService.findVisibleByIdOrThrowException(userId, new UserPrincipal(user));

        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("findVisibleByIdOrThrowException lets an ADMIN read a deactivated account")
    void findVisibleByIdOrThrowException_admin_returnsUser() {
        when(userRepository.findByUserIdAndAccountStatusNot(userId, AccountStatus.DELETED))
                .thenReturn(Optional.of(user));
        user.setAccountStatus(AccountStatus.DEACTIVATED);
        User admin = User.builder()
                .userId(UUID.randomUUID())
                .role(Role.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        User result = userService.findVisibleByIdOrThrowException(userId, new UserPrincipal(admin));

        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("reactivateAccount marks a DEACTIVATED account as ACTIVE and clears deactivatedAt")
    void reactivateAccount_deactivated_activatesAndClearsTimestamp() {
        user.setAccountStatus(AccountStatus.DEACTIVATED);
        user.setDeactivatedAt(LocalDateTime.now(FIXED_CLOCK).minusDays(2));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        User result = userService.reactivateAccount(userId);

        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.getDeactivatedAt()).isNull();
        assertThat(new UserPrincipal(result).isEnabled()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("reactivateAccount of an account that is not DEACTIVATED throws ACCOUNT_NOT_DEACTIVATED")
    void reactivateAccount_notDeactivated_throwsConflict() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.reactivateAccount(userId))
                .isInstanceOfSatisfying(BusinessConflictException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_NOT_DEACTIVATED");
                    assertThat(ex.getUserMessage()).isEqualTo("La cuenta no está desactivada.");
                });

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOptionalByEmail trims the email and returns the user when present")
    void findOptionalByEmail_trimsEmail() {
        when(userRepository.findByEmailIncludingDeleted("user@test.com")).thenReturn(Optional.of(user));

        Optional<User> result = userService.findOptionalByEmail("  user@test.com  ");

        assertThat(result).containsSame(user);
    }
}
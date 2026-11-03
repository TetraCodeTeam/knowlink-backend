package com.knowlink.api.users.validation;

import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.bookings.utils.BookingConstants;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.interfaces.ITokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserValidationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private IUserRepository userRepository;
    @Mock
    private ITokenService tokenService;
    @Mock
    private IBookingRepository bookingRepository;
    @Mock
    private IHoldRepository holdRepository;
    @Mock
    private SessionClaimRepository sessionClaimRepository;
    @Mock
    private IFundsTransferRepository fundsTransferRepository;

    private UserValidationService userValidationService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userValidationService = new UserValidationService(
                userRepository, tokenService, bookingRepository, holdRepository,
                sessionClaimRepository, fundsTransferRepository, FIXED_CLOCK);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("An active booking as student blocks deactivation")
    void activeBookingAsStudent_blocksDeactivation() {
        when(bookingRepository.existsByStudent_UserIdAndBookingStatusIn(
                userId, BookingConstants.ACTIVE_STATUSES)).thenReturn(true);

        assertConflict();
    }

    @Test
    @DisplayName("An active booking as tutor blocks deactivation")
    void activeBookingAsTutor_blocksDeactivation() {
        when(bookingRepository.existsByTutor_UserIdAndBookingStatusIn(
                userId, BookingConstants.ACTIVE_STATUSES)).thenReturn(true);

        assertConflict();
    }

    @Test
    @DisplayName("A live hold as student blocks deactivation")
    void activeHoldAsStudent_blocksDeactivation() {
        when(holdRepository.existsByStudent_UserIdAndExpiresAtAfter(
                userId, LocalDateTime.now(FIXED_CLOCK))).thenReturn(true);

        assertConflict();
    }

    @Test
    @DisplayName("A live hold on the tutor slots blocks deactivation")
    void activeHoldOnTutorSlots_blocksDeactivation() {
        when(holdRepository.existsActiveHoldOnTutorSlots(
                userId, LocalDateTime.now(FIXED_CLOCK))).thenReturn(true);

        assertConflict();
    }

    @Test
    @DisplayName("Only completed, cancelled or expired bookings and holds deactivates normally")
    void noActiveBookingsOrHolds_doesNotThrow() {
        assertThatCode(() -> userValidationService.ifUserHasActiveBookingsThrowException(userId))
                .doesNotThrowAnyException();

        verify(bookingRepository).existsByStudent_UserIdAndBookingStatusIn(
                userId, BookingConstants.ACTIVE_STATUSES);
        verify(bookingRepository).existsByTutor_UserIdAndBookingStatusIn(
                userId, BookingConstants.ACTIVE_STATUSES);
        verify(holdRepository).existsByStudent_UserIdAndExpiresAtAfter(
                userId, LocalDateTime.now(FIXED_CLOCK));
        verify(holdRepository).existsActiveHoldOnTutorSlots(
                userId, LocalDateTime.now(FIXED_CLOCK));
    }

    @Test
    @DisplayName("An active session claim blocks deactivation")
    void activeClaim_blocksDeactivation() {
        when(sessionClaimRepository.existsActiveClaimForUser(userId, ClaimStatus.ACTIVE_STATUSES))
                .thenReturn(true);

        assertThatThrownBy(() -> userValidationService.ifUserHasActiveClaimsOrRetainedFundsThrowException(userId))
                .isInstanceOfSatisfying(BusinessConflictException.class, ex -> {
                    org.assertj.core.api.Assertions.assertThat(ex.getErrorCode())
                            .isEqualTo("ACCOUNT_HAS_ACTIVE_CLAIMS");
                    org.assertj.core.api.Assertions.assertThat(ex.getUserMessage())
                            .isEqualTo("No pod\u00e9s dar de baja tu cuenta con reclamos abiertos");
                });
    }

    @Test
    @DisplayName("Retained funds block deactivation")
    void retainedFunds_blockDeactivation() {
        when(fundsTransferRepository.existsRetainedFundsForUser(userId, FundsStatus.RETAINED_STATUSES))
                .thenReturn(true);

        assertThatThrownBy(() -> userValidationService.ifUserHasActiveClaimsOrRetainedFundsThrowException(userId))
                .isInstanceOfSatisfying(BusinessConflictException.class, ex -> {
                    org.assertj.core.api.Assertions.assertThat(ex.getErrorCode())
                            .isEqualTo("ACCOUNT_HAS_RETAINED_FUNDS");
                    org.assertj.core.api.Assertions.assertThat(ex.getUserMessage())
                            .isEqualTo("No pod\u00e9s dar de baja tu cuenta con fondos retenidos pendientes");
                });
    }

    @Test
    @DisplayName("No active claims or retained funds does not block deactivation")
    void noActiveClaimsOrRetainedFunds_doesNotThrow() {
        assertThatCode(() -> userValidationService.ifUserHasActiveClaimsOrRetainedFundsThrowException(userId))
                .doesNotThrowAnyException();

        verify(sessionClaimRepository).existsActiveClaimForUser(userId, ClaimStatus.ACTIVE_STATUSES);
        verify(fundsTransferRepository).existsRetainedFundsForUser(userId, FundsStatus.RETAINED_STATUSES);
    }

    private void assertConflict() {
        assertThatThrownBy(() -> userValidationService.ifUserHasActiveBookingsThrowException(userId))
                .isInstanceOfSatisfying(BusinessConflictException.class, ex -> {
                    org.assertj.core.api.Assertions.assertThat(ex.getErrorCode())
                            .isEqualTo("ACCOUNT_HAS_ACTIVE_BOOKINGS");
                    org.assertj.core.api.Assertions.assertThat(ex.getUserMessage())
                            .isEqualTo("No pod\u00e9s dar de baja tu cuenta con reservas activas pendientes");
                });
    }
}
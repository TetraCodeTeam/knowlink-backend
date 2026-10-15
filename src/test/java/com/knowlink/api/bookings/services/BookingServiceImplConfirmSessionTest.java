package com.knowlink.api.bookings.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.mappers.BookingMapper;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.events.SessionConfirmedEvent;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.services.implementations.BookingServiceImpl;
import com.knowlink.api.bookings.services.interfaces.IBookingConfirmationTokenService;
import com.knowlink.api.bookings.validations.IBookingValidationService;
import com.knowlink.api.claims.services.interfaces.ISessionClaimService;
import com.knowlink.api.exceptions.custom_exceptions.ConfirmationBlockedByClaimException;
import com.knowlink.api.users.data.models.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplConfirmSessionTest {

    private static final String RAW_TOKEN = "1234";
    private static final String ENCRYPTED_TOKEN = "encrypted";

    @Mock
    private IBookingRepository bookingRepository;
    @Mock
    private IBookingValidationService bookingValidationService;
    @Mock
    private IBookingConfirmationTokenService confirmationTokenService;
    @Mock
    private ISessionClaimService sessionClaimService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private BookingMapper bookingMapper;

    @InjectMocks
    private BookingServiceImpl service;

    private UUID tutorId;
    private Booking booking;

    @BeforeEach
    void setUp() {
        tutorId = UUID.randomUUID();
        booking = Booking.builder()
                .bookingId(UUID.randomUUID())
                .bookingStatus(BookingStatus.IN_PROGRESS)
                .confirmationToken(ENCRYPTED_TOKEN)
                .tutor(User.builder().userId(tutorId).build())
                .build();
        when(bookingRepository.findByIdForUpdate(booking.getBookingId())).thenReturn(Optional.of(booking));
    }

    @Test
    @DisplayName("Confirmar - con reclamo activo: 422 CONFIRMATION_BLOCKED_BY_ACTIVE_CLAIM sin consumir intentos ni completar la sesion")
    void confirmSession_withActiveClaim_isBlocked() {
        when(sessionClaimService.hasActiveClaim(booking.getBookingId())).thenReturn(true);

        assertThatThrownBy(() -> service.confirmSession(tutorId, booking.getBookingId(), RAW_TOKEN))
                .isInstanceOf(ConfirmationBlockedByClaimException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CONFIRMATION_BLOCKED_BY_ACTIVE_CLAIM");

        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        assertThat(booking.getConfirmedAt()).isNull();
        verify(bookingRepository, never()).save(any());
        verify(bookingRepository, never()).incrementConfirmationTokenAttempts(any());
        verifyNoInteractions(confirmationTokenService, applicationEventPublisher);
    }

    @Test
    @DisplayName("Confirmar - sin reclamo activo y token valido: completa la sesion y publica el evento")
    void confirmSession_withoutActiveClaim_completesSession() {
        when(sessionClaimService.hasActiveClaim(booking.getBookingId())).thenReturn(false);
        when(confirmationTokenService.matches(RAW_TOKEN, ENCRYPTED_TOKEN)).thenReturn(true);

        service.confirmSession(tutorId, booking.getBookingId(), RAW_TOKEN);

        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getConfirmedAt()).isNotNull();
        verify(bookingRepository).save(booking);
        verify(applicationEventPublisher).publishEvent(any(SessionConfirmedEvent.class));
    }
}

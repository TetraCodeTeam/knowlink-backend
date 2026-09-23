package com.knowlink.api.payments.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.exceptions.custom_exceptions.ImmutableBookingException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundsResolutionServiceTest {

    @Mock
    private IBookingRepository bookingRepository;

    @Mock
    private IFundsTransferRepository fundsTransferRepository;

    @Mock
    private FundsLedgerService fundsLedgerService;

    @InjectMocks
    private FundsResolutionService fundsResolutionService;

    private Booking booking;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        booking = Booking.builder()
                .bookingId(bookingId)
                .amount(new BigDecimal("1500.00"))
                .bookingStatus(BookingStatus.IN_PROGRESS)
                .build();
    }

    @Test
    @DisplayName("Tutor presente, alumno presente - fondos liberados al tutor")
    void resolve_tutorPresent_studentPresent_releasesToTutor() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());
        when(fundsTransferRepository.existsByBooking_BookingIdAndFundsStatusIn(
                eq(bookingId), any())).thenReturn(false);

        fundsResolutionService.resolve(bookingId, true, true);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getBookingStatus()).isEqualTo(BookingStatus.COMPLETED);
        verify(fundsLedgerService).releaseToTutor(eq(booking), eq(BookingStatus.COMPLETED));
    }

    @Test
    @DisplayName("Tutor presente, alumno ausente - fondos liberados al tutor")
    void resolve_tutorPresent_studentAbsent_releasesToTutor() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());
        when(fundsTransferRepository.existsByBooking_BookingIdAndFundsStatusIn(
                eq(bookingId), any())).thenReturn(false);

        fundsResolutionService.resolve(bookingId, true, false);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getBookingStatus()).isEqualTo(BookingStatus.COMPLETED);
        verify(fundsLedgerService).releaseToTutor(eq(booking), eq(BookingStatus.COMPLETED));
    }

    @Test
    @DisplayName("Tutor ausente, alumno presente - fondos devueltos al alumno")
    void resolve_tutorAbsent_studentPresent_refundsToStudent() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());
        when(fundsTransferRepository.existsByBooking_BookingIdAndFundsStatusIn(
                eq(bookingId), any())).thenReturn(false);

        fundsResolutionService.resolve(bookingId, false, true);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getBookingStatus()).isEqualTo(BookingStatus.NOT_FULFILLED_BY_TUTOR);
        verify(fundsLedgerService).refundToStudent(eq(booking), eq(BookingStatus.NOT_FULFILLED_BY_TUTOR));
    }

    @Test
    @DisplayName("Tutor ausente, alumno ausente - fondos devueltos al alumno")
    void resolve_tutorAbsent_studentAbsent_refundsToStudent() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());
        when(fundsTransferRepository.existsByBooking_BookingIdAndFundsStatusIn(
                eq(bookingId), any())).thenReturn(false);

        fundsResolutionService.resolve(bookingId, false, false);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getBookingStatus()).isEqualTo(BookingStatus.SESSION_NOT_HELD);
        verify(fundsLedgerService).refundToStudent(eq(booking), eq(BookingStatus.SESSION_NOT_HELD));
    }

    @Test
    @DisplayName("Reclamo activo - fondos no se resuelven")
    void resolve_activeClaim_fundsNotResolved() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());
        when(fundsTransferRepository.existsByBooking_BookingIdAndFundsStatusIn(
                eq(bookingId), any())).thenReturn(true);

        fundsResolutionService.resolve(bookingId, true, true);

        verify(bookingRepository, never()).save(any());
        verify(fundsLedgerService, never()).releaseToTutor(any(), any());
    }

    @Test
    @DisplayName("Transferencia terminal existente - lanza ImmutableBookingException")
    void resolve_terminalTransferExists_throwsImmutableBookingException() {
        FundsTransfer existingTransfer = FundsTransfer.builder()
                .fundsStatus(FundsStatus.RELEASED_TO_TUTOR)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(fundsTransferRepository.findByBooking_BookingId(bookingId))
                .thenReturn(Optional.of(existingTransfer));

        assertThatThrownBy(() -> fundsResolutionService.resolve(bookingId, true, true))
                .isInstanceOf(ImmutableBookingException.class);
    }

    @Test
    @DisplayName("Reserva no encontrada - lanza ResourceNotFoundException")
    void resolve_bookingNotFound_throwsResourceNotFoundException() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fundsResolutionService.resolve(bookingId, true, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Reintento post-disputa - fondos suspendidos se desbloquean")
    void retryAfterClaimResolution_suspendedTransfer_unblocked() {
        FundsTransfer suspendedTransfer = FundsTransfer.builder()
                .fundsTransferId(UUID.randomUUID())
                .booking(booking)
                .fundsStatus(FundsStatus.SUSPENDED_BY_CLAIM)
                .blockingClaimId(UUID.randomUUID())
                .build();

        when(fundsTransferRepository.findByBooking_BookingIdAndFundsStatus(
                bookingId, FundsStatus.SUSPENDED_BY_CLAIM))
                .thenReturn(Optional.of(suspendedTransfer));

        fundsResolutionService.retryAfterClaimResolution(bookingId);

        verify(fundsTransferRepository).save(suspendedTransfer);
        assertThat(suspendedTransfer.getFundsStatus()).isEqualTo(FundsStatus.HELD);
        assertThat(suspendedTransfer.getBlockingClaimId()).isNull();
    }
}
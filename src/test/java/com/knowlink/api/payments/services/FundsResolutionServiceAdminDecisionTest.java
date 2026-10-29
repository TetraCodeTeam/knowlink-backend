package com.knowlink.api.payments.services;

import com.knowlink.api.admin.claims.data.enums.ClaimFavoredParty;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.exceptions.custom_exceptions.ImmutableBookingException;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FundsResolutionServiceAdminDecisionTest {

    @Mock
    private IBookingRepository bookingRepository;

    @Mock
    private SessionClaimRepository sessionClaimRepository;

    @Mock
    private IFundsTransferRepository fundsTransferRepository;

    @Mock
    private FundsLedgerService fundsLedgerService;

    @InjectMocks
    private FundsResolutionService fundsResolutionService;

    private UUID bookingId;
    private UUID claimId;
    private Booking booking;
    private FundsTransfer transfer;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        claimId = UUID.randomUUID();
        booking = Booking.builder()
                .bookingId(bookingId)
                .amount(new BigDecimal("1000.00"))
                .bookingStatus(BookingStatus.NOT_CONFIRMED)
                .build();
        transfer = FundsTransfer.builder()
                .fundsTransferId(UUID.randomUUID())
                .booking(booking)
                .originalAmount(new BigDecimal("1000.00"))
                .systemRetentionPercentage(new BigDecimal("3.00"))
                .transferredAmount(BigDecimal.ZERO)
                .recipient(FundsRecipient.TUTOR)
                .concept("Fondos retenidos para reserva")
                .fundsStatus(FundsStatus.SUSPENDED_BY_CLAIM)
                .blockingClaimId(claimId)
                .build();
        when(fundsLedgerService.calculateTutorTransferredAmount(new BigDecimal("1000.00")))
                .thenReturn(new BigDecimal("970.00"));
    }

    @Test
    @DisplayName("Sin fila de fondos (sesión gratuita) no mueve nada y devuelve 0")
    void noFundsTransferIsNoOp() {
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());

        BigDecimal moved = fundsResolutionService.resolveByAdminDecision(
                booking, ClaimFavoredParty.TUTOR, claimId);

        assertThat(moved).isEqualByComparingTo("0");
        verify(fundsTransferRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fondos ya en estado terminal: no se duplica el movimiento")
    void terminalFundsThrowsImmutableBooking() {
        transfer.setFundsStatus(FundsStatus.RELEASED_TO_TUTOR);
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> fundsResolutionService.resolveByAdminDecision(
                booking, ClaimFavoredParty.TUTOR, claimId))
                .isInstanceOf(ImmutableBookingException.class);
        verify(fundsTransferRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fondos no suspendidos: FUNDS_STATE_MISMATCH")
    void notSuspendedFundsThrowsStateMismatch() {
        transfer.setFundsStatus(FundsStatus.HELD);
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> fundsResolutionService.resolveByAdminDecision(
                booking, ClaimFavoredParty.TUTOR, claimId))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(ex -> assertThat(((BusinessConflictException) ex).getErrorCode())
                        .isEqualTo("FUNDS_STATE_MISMATCH"));
    }

    @Test
    @DisplayName("Fondos suspendidos por otro reclamo: FUNDS_STATE_MISMATCH")
    void differentBlockingClaimThrowsStateMismatch() {
        transfer.setBlockingClaimId(UUID.randomUUID());
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> fundsResolutionService.resolveByAdminDecision(
                booking, ClaimFavoredParty.TUTOR, claimId))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(ex -> assertThat(((BusinessConflictException) ex).getErrorCode())
                        .isEqualTo("FUNDS_STATE_MISMATCH"));
    }

    @Test
    @DisplayName("A favor del tutor: actualiza la fila existente con la retención del sistema")
    void favoredTutorUpdatesExistingRow() {
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(transfer));

        BigDecimal moved = fundsResolutionService.resolveByAdminDecision(
                booking, ClaimFavoredParty.TUTOR, claimId);

        assertThat(moved).isEqualByComparingTo("970.00");
        verify(fundsTransferRepository).save(transfer);
        assertThat(transfer.getRecipient()).isEqualTo(FundsRecipient.TUTOR);
        assertThat(transfer.getFundsStatus()).isEqualTo(FundsStatus.RELEASED_TO_TUTOR);
        assertThat(transfer.getTransferredAmount()).isEqualByComparingTo("970.00");
        assertThat(transfer.getConcept()).isEqualTo("Liberación por resolución de reclamo a favor del tutor");
        assertThat(transfer.getProcessedAt()).isNotNull();
        assertThat(transfer.getBlockingClaimId()).isEqualTo(claimId);
    }

    @Test
    @DisplayName("A favor del alumno: actualiza la fila existente con reintegro total")
    void favoredStudentUpdatesExistingRow() {
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(transfer));

        BigDecimal moved = fundsResolutionService.resolveByAdminDecision(
                booking, ClaimFavoredParty.STUDENT, claimId);

        assertThat(moved).isEqualByComparingTo("1000.00");
        verify(fundsTransferRepository).save(transfer);
        assertThat(transfer.getRecipient()).isEqualTo(FundsRecipient.STUDENT);
        assertThat(transfer.getFundsStatus()).isEqualTo(FundsStatus.REFUNDED_TO_STUDENT);
        assertThat(transfer.getTransferredAmount()).isEqualByComparingTo("1000.00");
        assertThat(transfer.getSystemRetentionPercentage()).isEqualByComparingTo("0");
        assertThat(transfer.getConcept()).isEqualTo("Devolución por resolución de reclamo a favor del alumno");
    }

    @Test
    @DisplayName("Reintento de fondos con reclamo abierto: CLAIM_STILL_OPEN sin desbloquear")
    void retryWithOpenClaimIsRejected() {
        when(sessionClaimRepository.existsByBooking_BookingIdAndStatusIn(
                bookingId, ClaimStatus.ACTIVE_STATUSES)).thenReturn(true);

        assertThatThrownBy(() -> fundsResolutionService.retryAfterClaimResolution(bookingId))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(ex -> assertThat(((BusinessConflictException) ex).getErrorCode())
                        .isEqualTo("CLAIM_STILL_OPEN"));
        verify(fundsTransferRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reintento de fondos sin reclamo abierto: desbloquea la fila suspendida")
    void retryWithoutOpenClaimUnblocks() {
        when(sessionClaimRepository.existsByBooking_BookingIdAndStatusIn(
                bookingId, ClaimStatus.ACTIVE_STATUSES)).thenReturn(false);
        when(fundsTransferRepository.findByBooking_BookingIdAndFundsStatus(
                bookingId, FundsStatus.SUSPENDED_BY_CLAIM)).thenReturn(Optional.of(transfer));

        fundsResolutionService.retryAfterClaimResolution(bookingId);

        verify(fundsTransferRepository).save(transfer);
        assertThat(transfer.getFundsStatus()).isEqualTo(FundsStatus.HELD);
        assertThat(transfer.getBlockingClaimId()).isNull();
    }
}
package com.knowlink.api.payments.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.events.FundsTransferProcessedEvent;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundsLedgerServiceTest {

    @Mock
    private IFundsTransferRepository fundsTransferRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private FundsLedgerService fundsLedgerService;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID claimId = UUID.randomUUID();

    @Test
    @DisplayName("Retencion - transferencia HELD se suspende por reclamo y guarda el bloqueo")
    void markSuspendedByClaim_heldTransfer_suspendsAndKeepsClaimId() {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository, applicationEventPublisher);
        FundsTransfer transfer = FundsTransfer.builder()
                .fundsTransferId(UUID.randomUUID())
                .fundsStatus(FundsStatus.HELD)
                .build();
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(transfer));

        fundsLedgerService.markSuspendedByClaim(bookingId, claimId);

        assertThat(transfer.getFundsStatus()).isEqualTo(FundsStatus.SUSPENDED_BY_CLAIM);
        assertThat(transfer.getBlockingClaimId()).isEqualTo(claimId);
        verify(fundsTransferRepository).save(transfer);
    }

    @Test
    @DisplayName("Retencion idempotente - ya suspendida conserva el claim original y no vuelve a guardar")
    void markSuspendedByClaim_alreadySuspended_keepsOriginalBlockingClaim() {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository, applicationEventPublisher);
        UUID originalClaimId = UUID.randomUUID();
        FundsTransfer transfer = FundsTransfer.builder()
                .fundsTransferId(UUID.randomUUID())
                .fundsStatus(FundsStatus.SUSPENDED_BY_CLAIM)
                .blockingClaimId(originalClaimId)
                .build();
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(transfer));

        fundsLedgerService.markSuspendedByClaim(bookingId, claimId);

        assertThat(transfer.getFundsStatus()).isEqualTo(FundsStatus.SUSPENDED_BY_CLAIM);
        assertThat(transfer.getBlockingClaimId()).isEqualTo(originalClaimId);
        verify(fundsTransferRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sesion gratuita - sin transferencia no se modifica el libro de fondos")
    void markSuspendedByClaim_noTransfer_doesNothing() {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository, applicationEventPublisher);
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());

        fundsLedgerService.markSuspendedByClaim(bookingId, claimId);

        verify(fundsTransferRepository, never()).save(any());
        ArgumentCaptor<FundsTransfer> captor = ArgumentCaptor.forClass(FundsTransfer.class);
        verify(fundsTransferRepository, never()).save(captor.capture());
    }

    @Test
    @DisplayName("Liberacion - publica FundsTransferProcessedEvent con el monto transferido al tutor")
    void releaseToTutor_publishesFundsTransferProcessedEvent() throws Exception {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository, applicationEventPublisher);
        injectRetention(new BigDecimal("3.00"));
        Booking booking = Booking.builder().bookingId(UUID.randomUUID()).amount(new BigDecimal("2100.00")).build();

        fundsLedgerService.releaseToTutor(booking, BookingStatus.COMPLETED);

        ArgumentCaptor<FundsTransferProcessedEvent> captor = ArgumentCaptor.forClass(FundsTransferProcessedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        FundsTransferProcessedEvent event = captor.getValue();
        assertThat(event.bookingId()).isEqualTo(booking.getBookingId());
        assertThat(event.recipient()).isEqualTo(FundsRecipient.TUTOR);
        assertThat(event.transferredAmount()).isEqualByComparingTo("2037.00");
        assertThat(event.concept()).isEqualTo("Liberacion por sesion completada");
    }

    @Test
    @DisplayName("Devolucion - publica FundsTransferProcessedEvent con el monto devuelto al alumno")
    void refundToStudent_publishesFundsTransferProcessedEvent() throws Exception {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository, applicationEventPublisher);
        Booking booking = Booking.builder().bookingId(UUID.randomUUID()).amount(new BigDecimal("2100.00")).build();

        fundsLedgerService.refundToStudent(booking, BookingStatus.SESSION_NOT_HELD);

        ArgumentCaptor<FundsTransferProcessedEvent> captor = ArgumentCaptor.forClass(FundsTransferProcessedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        FundsTransferProcessedEvent event = captor.getValue();
        assertThat(event.bookingId()).isEqualTo(booking.getBookingId());
        assertThat(event.recipient()).isEqualTo(FundsRecipient.STUDENT);
        assertThat(event.transferredAmount()).isEqualByComparingTo("2100.00");
        assertThat(event.concept()).isEqualTo("Devolucion por sesion no realizada");
    }

    private void injectRetention(BigDecimal retention) throws Exception {
        Field field = FundsLedgerService.class.getDeclaredField("systemRetentionPercentage");
        field.setAccessible(true);
        field.set(fundsLedgerService, retention);
    }
}
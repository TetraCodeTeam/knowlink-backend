package com.knowlink.api.payments.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsConcept;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.events.FundsSuspendedByClaimEvent;
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
    @DisplayName("Retencion - transferencia HELD se suspende por reclamo y publica el evento")
    void markSuspendedByClaim_heldTransfer_suspendsAndPublishesEvent() {
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
        ArgumentCaptor<FundsSuspendedByClaimEvent> captor = ArgumentCaptor.forClass(FundsSuspendedByClaimEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().bookingId()).isEqualTo(bookingId);
        assertThat(captor.getValue().claimId()).isEqualTo(claimId);
    }

    @Test
    @DisplayName("Retencion idempotente - ya suspendida conserva el claim original y no publica eventos")
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
        verify(applicationEventPublisher, never()).publishEvent(any(FundsSuspendedByClaimEvent.class));
    }

    @Test
    @DisplayName("Sesion gratuita - sin transferencia no se modifica el libro de fondos")
    void markSuspendedByClaim_noTransfer_doesNothing() {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository, applicationEventPublisher);
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());

        fundsLedgerService.markSuspendedByClaim(bookingId, claimId);

        verify(fundsTransferRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any(FundsSuspendedByClaimEvent.class));
    }

    @Test
    @DisplayName("Liberacion con alumno presente - concepto de sesion completada y evento con retencion")
    void releaseToTutor_studentPresent_publishesCompletedConcept() throws Exception {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository, applicationEventPublisher);
        injectRetention(new BigDecimal("3.00"));
        Booking booking = Booking.builder().bookingId(bookingId).amount(new BigDecimal("2100.00")).build();
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());

        fundsLedgerService.releaseToTutor(booking, BookingStatus.COMPLETED, true);

        ArgumentCaptor<FundsTransferProcessedEvent> captor = ArgumentCaptor.forClass(FundsTransferProcessedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        FundsTransferProcessedEvent event = captor.getValue();
        assertThat(event.bookingId()).isEqualTo(bookingId);
        assertThat(event.recipient()).isEqualTo(FundsRecipient.TUTOR);
        assertThat(event.originalAmount()).isEqualByComparingTo("2100.00");
        assertThat(event.transferredAmount()).isEqualByComparingTo("2037.00");
        assertThat(event.retentionPercentage()).isEqualByComparingTo("3.00");
        assertThat(event.concept()).isEqualTo(FundsConcept.RELEASED_SESSION_COMPLETED);
    }

    @Test
    @DisplayName("Liberacion con alumno ausente - concepto de alumno ausente")
    void releaseToTutor_studentAbsent_publishesAbsentStudentConcept() throws Exception {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository, applicationEventPublisher);
        injectRetention(new BigDecimal("3.00"));
        Booking booking = Booking.builder().bookingId(bookingId).amount(new BigDecimal("2100.00")).build();
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());

        fundsLedgerService.releaseToTutor(booking, BookingStatus.COMPLETED, false);

        ArgumentCaptor<FundsTransferProcessedEvent> captor = ArgumentCaptor.forClass(FundsTransferProcessedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().concept()).isEqualTo(FundsConcept.RELEASED_STUDENT_ABSENT);
    }

    @Test
    @DisplayName("Liberacion - la fila HELD existente se actualiza en lugar de duplicarse")
    void releaseToTutor_existingHeldRow_updatesInPlace() throws Exception {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository, applicationEventPublisher);
        injectRetention(new BigDecimal("3.00"));
        Booking booking = Booking.builder().bookingId(bookingId).amount(new BigDecimal("2100.00")).build();
        FundsTransfer held = FundsTransfer.builder()
                .fundsTransferId(UUID.randomUUID())
                .booking(booking)
                .fundsStatus(FundsStatus.HELD)
                .concept("Fondos retenidos para reserva")
                .build();
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.of(held));

        fundsLedgerService.releaseToTutor(booking, BookingStatus.COMPLETED, true);

        verify(fundsTransferRepository).save(held);
        assertThat(held.getFundsStatus()).isEqualTo(FundsStatus.RELEASED_TO_TUTOR);
        assertThat(held.getConcept()).isEqualTo("Liberacion por sesion completada");
        assertThat(held.getTransferredAmount()).isEqualByComparingTo("2037.00");
    }

    @Test
    @DisplayName("Devolucion - publica FundsTransferProcessedEvent con el monto devuelto al alumno")
    void refundToStudent_publishesFundsTransferProcessedEvent() {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository, applicationEventPublisher);
        Booking booking = Booking.builder().bookingId(bookingId).amount(new BigDecimal("2100.00")).build();
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());

        fundsLedgerService.refundToStudent(booking, BookingStatus.SESSION_NOT_HELD);

        ArgumentCaptor<FundsTransferProcessedEvent> captor = ArgumentCaptor.forClass(FundsTransferProcessedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        FundsTransferProcessedEvent event = captor.getValue();
        assertThat(event.bookingId()).isEqualTo(bookingId);
        assertThat(event.recipient()).isEqualTo(FundsRecipient.STUDENT);
        assertThat(event.originalAmount()).isEqualByComparingTo("2100.00");
        assertThat(event.transferredAmount()).isEqualByComparingTo("2100.00");
        assertThat(event.retentionPercentage()).isNull();
        assertThat(event.concept()).isEqualTo(FundsConcept.REFUNDED_SESSION_NOT_HELD);
    }

    @Test
    @DisplayName("Devolucion por tutor ausente - concepto especifico")
    void refundToStudent_absentTutor_publishesAbsentTutorConcept() {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository, applicationEventPublisher);
        Booking booking = Booking.builder().bookingId(bookingId).amount(new BigDecimal("2100.00")).build();
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());

        fundsLedgerService.refundToStudent(booking, BookingStatus.NOT_FULFILLED_BY_TUTOR);

        ArgumentCaptor<FundsTransferProcessedEvent> captor = ArgumentCaptor.forClass(FundsTransferProcessedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().concept()).isEqualTo(FundsConcept.REFUNDED_TUTOR_ABSENT);
    }

    @Test
    @DisplayName("Retencion inicial - createHeldTransfer guarda la fila y publica el evento")
    void createHeldTransfer_savesRowAndPublishesEvent() {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository, applicationEventPublisher);
        Booking booking = Booking.builder().bookingId(bookingId).amount(new BigDecimal("2100.00")).build();

        fundsLedgerService.createHeldTransfer(booking);

        verify(fundsTransferRepository).save(any(FundsTransfer.class));
        ArgumentCaptor<FundsTransferProcessedEvent> captor = ArgumentCaptor.forClass(FundsTransferProcessedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        FundsTransferProcessedEvent event = captor.getValue();
        assertThat(event.bookingId()).isEqualTo(bookingId);
        assertThat(event.originalAmount()).isEqualByComparingTo("2100.00");
        assertThat(event.transferredAmount()).isEqualByComparingTo("0");
        assertThat(event.concept()).isEqualTo(FundsConcept.HELD);
    }

    private void injectRetention(BigDecimal retention) throws Exception {
        Field field = FundsLedgerService.class.getDeclaredField("systemRetentionPercentage");
        field.setAccessible(true);
        field.set(fundsLedgerService, retention);
    }
}
package com.knowlink.api.payments.services;

import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundsLedgerServiceTest {

    @Mock
    private IFundsTransferRepository fundsTransferRepository;

    private FundsLedgerService fundsLedgerService;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID claimId = UUID.randomUUID();

    @Test
    @DisplayName("Retencion - transferencia HELD se suspende por reclamo y guarda el bloqueo")
    void markSuspendedByClaim_heldTransfer_suspendsAndKeepsClaimId() {
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository);
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
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository);
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
        fundsLedgerService = new FundsLedgerService(fundsTransferRepository);
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());

        fundsLedgerService.markSuspendedByClaim(bookingId, claimId);

        verify(fundsTransferRepository, never()).save(any());
        ArgumentCaptor<FundsTransfer> captor = ArgumentCaptor.forClass(FundsTransfer.class);
        verify(fundsTransferRepository, never()).save(captor.capture());
    }
}